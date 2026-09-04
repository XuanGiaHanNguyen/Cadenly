# Cadenly — Smart Meeting Scheduler

A polyglot system that turns a meeting recording into optimally scheduled, conflict-free tasks — end to end, with no cloud dependency. It listens, transcribes, summarizes, extracts action items, resolves who owns them and when they're due, and slots them into real calendars under real concurrent load, broadcasting every change live.

Built as a deep dive into applying classical CS fundamentals — dynamic programming, concurrency control, graph algorithms — to a real, working system rather than isolated exercises.

## What it actually does

```
Audio recording
      │
      ▼
Whisper transcription (local, faster-whisper)
      │
      ▼
TextRank summarization (hand-implemented: TF-IDF → cosine similarity → weighted PageRank)
      │
      ▼
Task extraction (local Llama 3.1 8B via Ollama, JSON-prompted)
      │
      ▼
Deadline resolution (dateparser, resolved against the meeting's actual timestamp)
      │
      ▼
Owner resolution (name matching against a live user directory)
      │
      ▼
Weighted interval scheduling (dynamic programming, maximizes priority-weighted throughput)
      │
      ▼
Concurrency-safe commit (per-resource locking, prevents double-booking under real load)
      │
      ▼
Live broadcast (WebSocket, ~34ms to connected clients)
```

Given a transcript and existing calendars, the system doesn't just place tasks first-come-first-served — it solves for the subset of tasks that maximizes total priority-weighted value scheduled before their deadlines, proven with dynamic programming rather than a greedy heuristic.

## Architecture

Two independently-tested services, split deliberately by strength rather than convenience:

| | `scheduler-engine` | `recording-pipeline` |
|---|---|---|
| **Language** | Java (Spring Boot) | Python (FastAPI) |
| **Owns** | Task model, free-slot detection, DP scheduling, priority queues, locking, WebSocket broadcast | Audio ingestion, transcription, summarization, task extraction, deadline resolution |
| **Why this language** | Mature, provable concurrency primitives (`ReentrantLock`, `ConcurrentHashMap`) | Best-in-class local NLP/ML ecosystem (Whisper, Ollama) |
| **External dependencies** | None — everything local | None — Whisper and Llama both run fully offline |

Communication: the recording pipeline calls the scheduler engine's REST endpoint (`POST /api/tasks/submit`) once tasks are extracted and resolved. The seam is a single method (`SchedulingService.submitTasks(...)`), designed from the start to be swappable for a message queue without touching either service's internals.

## The algorithms, and why each one was chosen

**Weighted Interval Scheduling (DP), not greedy.** Free time is computed via sweep-line interval merging, then each task is reduced to its earliest-fit candidate interval, and a classic O(n log n) DP (binary-search compatibility lookup + backtrack reconstruction) selects the subset maximizing total priority-weighted value. Proven against a greedy earliest-deadline-first baseline with a constructed adversarial case: **DP scores 120 vs. greedy's 100** on a case where greedy grabs one long high-weight task and gets blocked from three shorter tasks whose combined weight is actually higher.

**Per-resource pessimistic locking, not optimistic CAS.** Multiple users can book the same shared resource (a room, a person's calendar) simultaneously. Benchmarked both strategies under 200 concurrent threads:

| Scenario | Pessimistic | Optimistic |
|---|---|---|
| Same slot, one resource (high real contention) | 52,726 ops/sec | 37,607 ops/sec (22 wasted retries) |
| Distinct slots, one resource (no logical conflict) | 70,962 ops/sec | 30,474 ops/sec (**349 wasted retries**) |

The second row is the interesting result: even when all 200 writes are logically compatible, both strategies still serialize on the shared object — optimistic pays for it in wasted CAS retries, pessimistic just queues with zero wasted work. Real evidence for a tradeoff usually stated only in theory.

**TextRank, implemented from scratch.** Sentence-similarity graph built from hand-written TF-IDF vectors and cosine similarity, ranked via weighted PageRank power iteration (Mihalcea & Tarau, 2004), hand-verified against manually computed expected scores on a small graph. Extended with a redundancy-suppression step beyond the vanilla algorithm — near-duplicate sentences no longer crowd out distinct but slightly-lower-scored ones. Demonstrated with a real before/after: without suppression, two near-duplicate sentences both appear in the top-k; with it, the duplicate is displaced by the distinct sentence.

## Concurrency correctness — proven, not assumed

A deterministic race-condition harness reproduces double-booking on purpose: an artificial delay between check and write widens the race window so the failure is reliable, not occasional. Against the unlocked implementation, **10/10 concurrent threads succeed in booking the same slot** — full corruption. Against the locked implementation under identical load, **exactly 1 succeeds**, the rest correctly reject.

While building the load-testing harness, a genuinely separate, pre-existing flaky test was found and fixed: `HashMap.computeIfAbsent`'s own unsynchronized structural mutation (not the intended check-then-act race) was occasionally throwing `ConcurrentModificationException` on first-insert under concurrent access — a subtler, stricter hazard than the one the test was designed to demonstrate. Isolated by pre-seeding the map entry before the concurrent phase starts; verified with 30 consecutive clean runs.

## Real-time updates

Booking success publishes a domain event (`ResourceBookedEvent`) via Spring's `ApplicationEventPublisher`, decoupled from the locking logic entirely — `SharedResourceCalendar` has zero WebSocket knowledge. A separate listener bridges the event to a STOMP broadcast. Verified end-to-end with a scripted STOMP client: booking → broadcast in **34ms**, against a 100ms target.

## Local AI, deliberately

Both the transcription and extraction models run entirely on-device:

- **faster-whisper** (CPU, int8-quantized) — chosen over the hosted Whisper API specifically because meeting audio is sensitive by nature; "never leaves the machine" outweighed a marginal accuracy gain.
- **Llama 3.1 8B via Ollama** for task extraction — originally built against Claude's forced tool-calling, then migrated to a fully local model to eliminate API cost entirely. The migration surfaced a real empirical finding: Ollama's OpenAI-compatible tool-calling shim double-encoded its JSON output in ~50% of test runs, while direct JSON-prompting (schema described in the system prompt, strict Pydantic validation on the response) had zero parse failures across 12 runs — so JSON-prompting was chosen for reliability, not convenience.

The safety-critical behavior — never hallucinating a task that wasn't stated — was stress-tested adversarially, not just on the original golden transcripts: 15 repeated runs across the original no-action-item transcript plus two adversarial variants designed to trip it up (a mention of a *completed* task, and small talk using words like "deadline" with no actual ask). **15/15 clean**, zero hallucinated tasks.

## Load testing — full-system numbers

| Test | Result |
|---|---|
| scheduler-engine, 200 concurrent requests, distinct owners | 980 req/sec, p50 184ms, all placed |
| scheduler-engine, 200 concurrent requests, one contended owner | **2,982 req/sec**, p50 8ms — faster, because failing fast beats a full free-slot search once the calendar fills |
| recording-pipeline, task extraction latency (1 / 5 / 15-min transcripts) | 11.2s → 136.6s → 408.6s — the dominant, worsening bottleneck as meetings get longer |
| recording-pipeline, transcription latency (1 / 5 / 15-min transcripts) | 6.9s → 35.3s → 85.4s |
| End-to-end pipeline throughput | 237 meetings/hour sustained (sequential by design — a single local model instance makes concurrent calls measure queuing, not throughput) |

The extraction-vs-transcription scaling gap is the clearest actionable finding: at real meeting lengths, LLM extraction — not audio processing — is the bottleneck worth optimizing first (e.g., chunking long transcripts instead of a single call).

## Task resolution — honest by design

Extracted tasks resolve into one of three explicit outcomes, never a silent guess:

- **Placed** — resolved owner, successfully scheduled
- **Rejected** — resolved owner, but no slot could be found before the deadline (or lost a genuine concurrency race at commit time)
- **Unresolved** — no confident owner match; excluded from scheduling and reported separately rather than silently assigned to a placeholder

This mirrors a rule followed throughout the project: a boundary that silently repairs or guesses at bad data is worse than one that fails loud and reports the ambiguity honestly. The same principle governs malformed LLM output (rejected with a clear error, never coerced) and unresolvable deadlines (a documented conservative fallback, not a fabricated precise date).

## Tech stack

**scheduler-engine:** Java, Spring Boot, `ReentrantLock` / `ConcurrentHashMap`, STOMP over WebSocket, JUnit
**recording-pipeline:** Python, FastAPI, faster-whisper, Ollama (Llama 3.1 8B), `dateparser`, numpy, pytest
**dashboard:** Next.js, `@stomp/stompjs`

## Project structure

```
scheduler-engine/     Java scheduling engine — algorithms, concurrency, REST, WebSocket
recording-pipeline/   Python recording/AI pipeline — transcription, summarization, extraction
dashboard/            Next.js live-updating dashboard
```

## Running it

```bash
# scheduler-engine
cd scheduler-engine && mvn spring-boot:run    # localhost:8080

# recording-pipeline (requires Ollama running with llama3.1:8b pulled)
cd recording-pipeline && source .venv/bin/activate
uvicorn app.main:app --reload                 # localhost:8000

# dashboard
cd dashboard && npm install && npm run dev    # localhost:3000
```

## Testing

```bash
# scheduler-engine — fast suite (default) vs. load tests
cd scheduler-engine && mvn test                          # 37 tests
mvn test -Dgroups=load -Dsurefire.excludedGroups=         # throughput/latency benchmarks

# recording-pipeline — fast suite (default, no live model calls) vs. real-model tests
cd recording-pipeline && python -m pytest     # 44 tests, mocked dependencies
python -m pytest -m slow                      # requires live Ollama + Whisper
```

Every phase of this project was built with a strict fast/slow test split: default runs are fully offline and deterministic (dependencies faked via DI), while a separate marked suite exercises the real local models for genuine end-to-end confidence — run deliberately, not on every commit.
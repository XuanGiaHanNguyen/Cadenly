# Phase 8: Load Testing Results

Real numbers from actual runs against a live Service A (embedded server) and live Service B + Ollama (llama3.1:8b) + faster-whisper, all on the same development machine. Absolute milliseconds are machine-dependent; the relative comparisons (which stage/scenario is slower, and by how much) are the more portable takeaway.

## 1. Service A — `POST /api/tasks/submit` under concurrent load

**Method:** `TaskSubmissionLoadTest` (`service-a/src/test/java/.../loadtest/`), tagged `@Tag("load")` and excluded from the default `mvn test` run. Spins up a real embedded server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) and drives it with 200 real concurrent HTTP requests via `TestRestTemplate`, using `ConcurrencyHarness.runConcurrentlyTimed` (the same gated `CountDownLatch` + `ExecutorService` pattern as Phase 3's benchmark, generalized to record per-request latency). Run explicitly with `mvn test -Dsurefire.excludedGroups= -Dgroups=load`.

| Metric | Scenario A: 200 distinct owners | Scenario B: 200 requests, 1 owner |
|---|---|---|
| Requests | 200 | 200 |
| Throughput | 980 req/sec | 2,982 req/sec |
| p50 latency | 184 ms | 8 ms |
| p95 latency | 198 ms | 16 ms |
| p99 latency | 200 ms | 19 ms |
| Errors | 0 | 0 |
| Placed | 200 | 3 |
| Rejected | 0 | 197 |
| Unresolved | 0 | 0 |

Scenario A gives every request its own owner (via the new `loadtest-user-<n>` deterministic-UUID pattern in `UserDirectoryService`) and a generous 7-day deadline window, so it measures the REST/scheduling stack's raw throughput ceiling with no lock contention: all 200 placed cleanly. Scenario B sends 200 concurrent single-task requests at the *same* owner within a tight 2-hour window that only fits ~4 non-overlapping 30-minute slots — a realistic "many people submit to one busy calendar at once" scenario. Counter-intuitively, Scenario B is both faster *and* higher-throughput than Scenario A: once the first few slots fill, most of the 200 requests fail fast (no free slot found in a narrow 2-hour window) rather than doing full free-slot search over a 7-day window, so the *contended* scenario does less average work per request even though it's exercising the shared-lock path Phase 3 was built to stress-test. This is a genuine, correctly-reported outcome, not a bug: `placed=3` and `rejected=197` are exactly what a saturated calendar under concurrent load should produce.

## 2. Service B — per-stage pipeline latency by meeting length

**Method:** `service-b/scripts/load_test_pipeline_stages.py` (standalone script, not a pytest test — its job is producing a report). Generates real speech audio via macOS `say`/`afconvert` (same mechanism as Phase 5's fixtures) for 1/5/15-minute meeting scripts built from a rotating template pool, then times each stage using the *actual output* of the previous stage as input (transcribed text feeds summarize/extract, not the original script) — an honest per-stage breakdown, not synthetic disconnected inputs.

| Length | Transcript words | Transcribe | Summarize | Extract (Ollama) | Resolve deadlines | Total | Tasks found |
|---|---|---|---|---|---|---|---|
| 1 min | 184 | 6.9 s | 2 ms | 11.2 s | 1.04 s* | 19.2 s | 3 |
| 5 min | 887 | 35.3 s | 7 ms | 136.6 s | 0.10 s | 172.0 s | 18 |
| 15 min | 2,642 | 85.4 s | 5 ms | 408.6 s | 1.32 s* | 495.4 s | 48 |

*\*Deadline resolution's first call per process pays a one-time ~1-1.3s warm-up (`dateparser` loading its locale/model data); every call after that costs only 1-6ms — the 1-min and 15-min rows both happened to run in a fresh process, so their totals are dominated by that one-time cost, not per-task work.*

Extraction is the clear and worsening bottleneck: it's already the largest stage at 1 minute (11.2s vs. transcription's 6.9s) and pulls further ahead as meetings get longer (408.6s vs. 85.4s at 15 minutes — extraction is ~4.8x transcription instead of ~1.6x). Transcription itself scales cleanly and predictably, running consistently 9-11x faster than real-time regardless of length. Summarization and deadline resolution (after warm-up) are both negligible — neither will ever be the thing to optimize here. Two operational findings worth acting on: (1) the extraction call's timeout had been hardcoded at 30s, which the 5-minute transcript alone exceeded — now configurable via `OLLAMA_TIMEOUT_SECONDS`, but a production deployment needs a real answer for long meetings (chunking the transcript, a faster model, or GPU inference), not just a bigger timeout; (2) `dateparser`'s warm-up cost should be paid once at app startup (a dummy `resolve_deadline` call) rather than on a live request.

## 3. End-to-end pipeline throughput (`POST /pipeline/process`)

**Method:** `service-b/scripts/load_test_e2e_pipeline.py`, real HTTP calls against a live Service B (uvicorn) which in turn calls live Ollama and live Service A. **Sequential, not concurrent, deliberately**: Ollama is one local model instance, so concurrent pipeline calls would mostly queue behind each other rather than reveal real parallelism — that would measure queuing, not throughput. Five runs across the Phase 6 golden transcripts (short, single-meeting-length inputs).

| Run | Latency | Placed | Rejected | Unresolved |
|---|---|---|---|---|
| clear action items | 36.5 s | 2 | 0 | 0 |
| no action items | 1.6 s | 0 | 0 | 0 |
| vague deadline | 14.8 s | 0 | 0 | 1 |
| clear action items (rerun) | 17.0 s | 2 | 0 | 0 |
| vague deadline (rerun) | 6.0 s | 0 | 0 | 1 |

**Aggregate:** 76.0s wall time for 5 runs → **237 meetings/hour** sustained sequential throughput. Avg latency 15.2s; range 1.6s–36.5s.

The single biggest driver of variance here isn't transcript content, it's whether Ollama already has the model loaded: the very first run of the whole script pays a one-time ~20s+ model-load cost on top of normal extraction time (36.5s vs. its own 17.0s rerun for the identical transcript). This is the same class of finding as the `dateparser` warm-up above, just an order of magnitude larger — a production deployment should keep Ollama warm (a periodic keep-alive ping, or `ollama run`'s `--keep-alive` setting) rather than let a real user's first request after any idle period eat a 20-30 second penalty. The `vague deadline` transcript correctly lands in `unresolved` both times (not a bug — it has no resolvable deadline, matching Phase 6/7 behavior).

## Notes and caveats

- **Environment was disk-constrained throughout this run** (as low as ~155MB free on a 228GB volume at points, unrelated to this work). One 15-minute-tier run was killed outright by the OS under this pressure and had to be re-run in smaller, disk-checkpointed steps. Absolute latencies may include some of this noise; the relative stage/scenario comparisons above are the reliable part.
- **Pre-existing flaky test found and fixed**: Phase 3's `RaceConditionTest` (the deliberately-unsafe `HashMap`-based calendar demo) was throwing `ConcurrentModificationException` instead of producing its intended double-booking result in roughly 1 of 5 runs. Root cause: the test's 10 threads all raced `HashMap.computeIfAbsent`'s unsynchronized *first insert* for a shared key (`UnsafeSharedResourceCalendar.java:23`), not the `ConcurrentHashMap` recursive-update gotcha this might resemble at a glance — there's no `ConcurrentHashMap` in this class at all. That map-level race was incidental to, and separate from, the check-then-act race on the booking list the test actually exists to demonstrate. Fixed by adding `UnsafeSharedResourceCalendar.seedResource()` to pre-populate the map entry single-threaded before the concurrent phase starts, isolating the demo to the intended race. Verified with 30 consecutive clean runs (previously ~1-in-5 failure).
- `OllamaTaskExtractionService`'s request timeout is now configurable via `OLLAMA_TIMEOUT_SECONDS` (default unchanged at 30s) — needed to even measure the 5- and 15-minute tiers above, and a legitimate production improvement on its own.

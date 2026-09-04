"""
Phase 8 load test: full end-to-end pipeline throughput via real HTTP calls
to POST /pipeline/process. Requires a live Service B (uvicorn) AND a live
Service A (for the scheduling submission step).

Sequential, not concurrent, deliberately: Ollama is a single local model
instance, so concurrent /pipeline/process calls would mostly queue behind
each other rather than reveal real parallelism - that would measure
queuing, not throughput. Sequential repeated calls give the honest
per-meeting latency and a realistic "meetings processed per hour" figure,
which is also the right shape of metric for this workload (processing
recordings as they complete, not serving a high-QPS API).

Run with:
    python scripts/load_test_e2e_pipeline.py
"""

import os
import sys
import time
from datetime import datetime, timedelta, timezone

import httpx

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from tests.fixtures.golden_transcripts import CLEAR_ACTION_ITEMS, NO_ACTION_ITEMS, VAGUE_DEADLINE

SERVICE_B_URL = "http://localhost:8000/pipeline/process"

RUNS = [
    ("golden: clear action items", CLEAR_ACTION_ITEMS),
    ("golden: no action items", NO_ACTION_ITEMS),
    ("golden: vague deadline", VAGUE_DEADLINE),
    ("golden: clear action items (rerun)", CLEAR_ACTION_ITEMS),
    ("golden: vague deadline (rerun)", VAGUE_DEADLINE),
]


def main() -> None:
    results = []
    overall_start = time.perf_counter()

    for i, (label, text) in enumerate(RUNS):
        recording_timestamp = (datetime.now(timezone.utc) - timedelta(minutes=i)).isoformat()
        print(f"[{i + 1}/{len(RUNS)}] {label}...")

        start = time.perf_counter()
        response = httpx.post(
            SERVICE_B_URL,
            json={"text": text, "recording_timestamp": recording_timestamp},
            timeout=300.0,
        )
        latency_ms = (time.perf_counter() - start) * 1000

        response.raise_for_status()
        body = response.json()
        placed = len(body.get("placed", []))
        rejected = len(body.get("rejected", []))
        unresolved = len(body.get("unresolved", []))

        print(f"  {latency_ms:.0f}ms  placed={placed} rejected={rejected} unresolved={unresolved}")
        results.append({
            "label": label,
            "latency_ms": latency_ms,
            "placed": placed,
            "rejected": rejected,
            "unresolved": unresolved,
        })

    total_elapsed_s = time.perf_counter() - overall_start
    latencies = [r["latency_ms"] for r in results]

    print("\n=== Summary ===")
    print(f"{'run':<38}{'latency_ms':<12}{'placed':<8}{'rejected':<10}{'unresolved':<10}")
    for r in results:
        print(f"{r['label']:<38}{r['latency_ms']:<12.0f}{r['placed']:<8}{r['rejected']:<10}{r['unresolved']:<10}")

    print(f"\ntotal wall time: {total_elapsed_s:.1f}s for {len(RUNS)} runs")
    print(f"throughput: {len(RUNS) / total_elapsed_s * 3600:.0f} meetings/hour (sequential)")
    print(f"avg latency: {sum(latencies) / len(latencies):.0f}ms")
    print(f"min/max latency: {min(latencies):.0f}ms / {max(latencies):.0f}ms")


if __name__ == "__main__":
    main()

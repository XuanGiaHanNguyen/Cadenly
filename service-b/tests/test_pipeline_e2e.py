"""
First test exercising the whole system as one pipeline: real transcript
text in, a correctly-shaped, scheduled Task with a resolved owner UUID and
resolved deadline out of a live Service A.

Requires BOTH a locally-running Ollama (llama3.1:8b) AND a locally-running
Service A instance (default http://localhost:8080). Free and local, but
still marked slow - the default test run shouldn't require two external
services to be up.

CLEAR_ACTION_ITEMS is reused deliberately from the Phase 6 golden fixtures:
it mentions "John" and "Sarah", which match literal hardcoded entries in
Service A's UserDirectoryService (see JOHN_ID/SARAH_ID below) - this is
what lets the extracted tasks actually resolve to a real owner instead of
landing in `unresolved`.
"""

from datetime import datetime, timedelta, timezone

import pytest

from app.extraction.ollama_task_extraction_service import OllamaTaskExtractionService
from app.scheduling.pipeline import process_transcript
from app.scheduling.scheduler_client import SchedulerClient
from tests.fixtures.golden_transcripts import CLEAR_ACTION_ITEMS

# Must match UserDirectoryService.SARAH_ID / JOHN_ID in Service A exactly.
SARAH_ID = "11111111-1111-1111-1111-111111111111"
JOHN_ID = "22222222-2222-2222-2222-222222222222"


@pytest.mark.slow
def test_real_transcript_flows_through_ollama_and_service_a_to_a_scheduled_task():
    recording_timestamp = datetime.now(timezone.utc) - timedelta(hours=1)

    result = process_transcript(
        CLEAR_ACTION_ITEMS,
        recording_timestamp,
        OllamaTaskExtractionService(),
        SchedulerClient(),
    )

    assert result["placed"] or result["rejected"], "expected at least one resolved task (placed or rejected)"
    assert not any(u for u in result["unresolved"]), (
        f"expected John/Sarah to resolve via UserDirectoryService, got unresolved: {result['unresolved']}"
    )

    resolved_owners = {t["owner"] for t in result["placed"]} | {t["owner"] for t in result["rejected"]}
    assert resolved_owners <= {SARAH_ID, JOHN_ID}
    assert resolved_owners, "expected at least one task resolved to a known owner"

    for task in result["placed"]:
        start = datetime.fromisoformat(task["start"].replace("Z", "+00:00"))
        end = datetime.fromisoformat(task["end"].replace("Z", "+00:00"))
        assert start >= recording_timestamp
        assert end > start

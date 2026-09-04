import pytest

from app.extraction.ollama_task_extraction_service import OllamaTaskExtractionService
from tests.fixtures.golden_transcripts import CLEAR_ACTION_ITEMS, NO_ACTION_ITEMS, VAGUE_DEADLINE

# Requires a locally-running Ollama with llama3.1:8b pulled
# (http://localhost:11434 by default). Free and local, but still marked
# slow - the default test run shouldn't require an external service to be
# up. Loose, structural assertions only - output isn't deterministic even
# with temperature=0 for a local quantized model.

service = OllamaTaskExtractionService()


@pytest.mark.slow
def test_clear_action_items_are_extracted():
    tasks = service.extract(CLEAR_ACTION_ITEMS)

    assert len(tasks) >= 2
    for task in tasks:
        assert task.owner.strip() != ""
        assert task.description.strip() != ""
        assert 1 <= task.priority <= 10
        assert task.estimated_duration_minutes >= 1

    deadline_hints = " ".join(t.deadline_hint.lower() for t in tasks)
    assert "friday" in deadline_hints or "week" in deadline_hints


@pytest.mark.slow
def test_no_action_items_returns_empty_list():
    tasks = service.extract(NO_ACTION_ITEMS)
    assert tasks == []


@pytest.mark.slow
def test_vague_deadline_stays_as_raw_phrase():
    tasks = service.extract(VAGUE_DEADLINE)

    assert len(tasks) >= 1
    assert any("soon" in t.deadline_hint.lower() for t in tasks)
    # a fabricated calendar date would look nothing like "soon" - loosely
    # guard against the model resolving the phrase instead of leaving it raw
    for t in tasks:
        assert not any(char.isdigit() for char in t.deadline_hint)

import pytest

from app.extraction.base import MalformedExtractionError
from app.extraction.ollama_task_extraction_service import OllamaTaskExtractionService, _strip_markdown_fences
from app.extraction.prompt import build_user_message


def test_unreachable_ollama_fails_clearly_not_a_hang(monkeypatch):
    # Port 1 is guaranteed to refuse the connection - proves the timeout/
    # error path is clear rather than hanging, same lesson as the earlier
    # Claude-client hang investigation.
    monkeypatch.setenv("OLLAMA_BASE_URL", "http://localhost:1/v1")
    service = OllamaTaskExtractionService()

    with pytest.raises(Exception):
        service.extract("some transcript")


def test_strip_markdown_fences_removes_json_code_fence():
    fenced = '```json\n{"tasks": []}\n```'
    assert _strip_markdown_fences(fenced) == '{"tasks": []}'


def test_strip_markdown_fences_leaves_plain_json_unchanged():
    plain = '{"tasks": []}'
    assert _strip_markdown_fences(plain) == '{"tasks": []}'


def test_build_user_message_embeds_transcript():
    message = build_user_message("John will send the report by Friday.")
    assert "John will send the report by Friday." in message


class _FakeChoice:
    def __init__(self, content):
        self.message = type("M", (), {"content": content})


class _FakeResponse:
    def __init__(self, content):
        self.choices = [_FakeChoice(content)]


class _FakeClient:
    def __init__(self, content):
        self._content = content
        self.chat = self
        self.completions = self

    def create(self, **kwargs):
        return _FakeResponse(self._content)


def test_malformed_json_raises_malformed_extraction_error(monkeypatch):
    service = OllamaTaskExtractionService()
    monkeypatch.setattr(
        "app.extraction.ollama_task_extraction_service.OpenAI",
        lambda **kwargs: _FakeClient("not valid json at all"),
    )

    with pytest.raises(MalformedExtractionError):
        service.extract("some transcript")


def test_tasks_not_a_list_raises_malformed_extraction_error(monkeypatch):
    service = OllamaTaskExtractionService()
    monkeypatch.setattr(
        "app.extraction.ollama_task_extraction_service.OpenAI",
        lambda **kwargs: _FakeClient('{"tasks": "not-a-list"}'),
    )

    with pytest.raises(MalformedExtractionError):
        service.extract("some transcript")


def test_task_failing_schema_validation_raises_malformed_extraction_error(monkeypatch):
    service = OllamaTaskExtractionService()
    monkeypatch.setattr(
        "app.extraction.ollama_task_extraction_service.OpenAI",
        lambda **kwargs: _FakeClient('{"tasks": [{"owner": "John", "priority": 99}]}'),  # missing fields, priority out of range
    )

    with pytest.raises(MalformedExtractionError):
        service.extract("some transcript")


def test_well_formed_response_parses_correctly(monkeypatch):
    service = OllamaTaskExtractionService()
    monkeypatch.setattr(
        "app.extraction.ollama_task_extraction_service.OpenAI",
        lambda **kwargs: _FakeClient(
            '```json\n{"tasks": [{"owner": "Sarah", "description": "Update slides", '
            '"deadline_hint": "next week", "estimated_duration_minutes": 45, "priority": 7}]}\n```'
        ),
    )

    tasks = service.extract("some transcript")

    assert len(tasks) == 1
    assert tasks[0].owner == "Sarah"
    assert tasks[0].estimated_duration_minutes == 45

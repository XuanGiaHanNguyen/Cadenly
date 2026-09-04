from fastapi.testclient import TestClient

from app.api.routes import get_task_extraction_service
from app.extraction.base import MalformedExtractionError
from app.main import app

client = TestClient(app)


class FakeWellFormedExtractionService:
    def extract(self, transcript: str):
        from app.api.schemas import ExtractedTask

        return [
            ExtractedTask(
                owner="Sarah Kim",
                description="Update the slides for the board review",
                deadline_hint="next week",
                estimated_duration_minutes=45,
                priority=7,
            )
        ]


class FakeEmptyExtractionService:
    def extract(self, transcript: str):
        return []


class FakeMalformedExtractionService:
    def extract(self, transcript: str):
        raise MalformedExtractionError("Claude output failed schema validation: priority out of range")


def _override(fake_service):
    app.dependency_overrides[get_task_extraction_service] = lambda: fake_service


def test_extract_tasks_returns_well_formed_tasks():
    _override(FakeWellFormedExtractionService())
    try:
        response = client.post("/extract-tasks", json={"text": "some transcript"})
        assert response.status_code == 200
        body = response.json()
        assert len(body["tasks"]) == 1
        task = body["tasks"][0]
        assert task["owner"] == "Sarah Kim"
        assert task["priority"] == 7
        assert task["estimated_duration_minutes"] == 45
    finally:
        app.dependency_overrides.clear()


def test_extract_tasks_returns_empty_list_not_an_error():
    _override(FakeEmptyExtractionService())
    try:
        response = client.post("/extract-tasks", json={"text": "just small talk"})
        assert response.status_code == 200
        assert response.json()["tasks"] == []
    finally:
        app.dependency_overrides.clear()


def test_extract_tasks_returns_502_on_malformed_upstream_output():
    _override(FakeMalformedExtractionService())
    try:
        response = client.post("/extract-tasks", json={"text": "some transcript"})
        assert response.status_code == 502
    finally:
        app.dependency_overrides.clear()


def test_extract_tasks_rejects_empty_text():
    response = client.post("/extract-tasks", json={"text": "   "})
    assert response.status_code == 422

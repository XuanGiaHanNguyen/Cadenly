from fastapi.testclient import TestClient

from app.api.routes import get_scheduler_client, get_task_extraction_service
from app.api.schemas import ExtractedTask
from app.extraction.base import MalformedExtractionError
from app.main import app
from app.scheduling.scheduler_client import SchedulerClientError

client = TestClient(app)


class FakeExtractionService:
    def __init__(self, tasks=None, raises=None):
        self._tasks = tasks or []
        self._raises = raises

    def extract(self, transcript: str):
        if self._raises:
            raise self._raises
        return self._tasks


class FakeSchedulerClient:
    def __init__(self, response=None, raises=None):
        self._response = response or {"placed": [], "rejected": [], "unresolved": []}
        self._raises = raises
        self.last_submitted_tasks = None

    def submit_tasks(self, tasks):
        self.last_submitted_tasks = tasks
        if self._raises:
            raise self._raises
        return self._response


def _override(extraction_service=None, scheduler_client=None):
    if extraction_service is not None:
        app.dependency_overrides[get_task_extraction_service] = lambda: extraction_service
    if scheduler_client is not None:
        app.dependency_overrides[get_scheduler_client] = lambda: scheduler_client


def test_process_pipeline_extracts_resolves_and_submits():
    extraction = FakeExtractionService(tasks=[
        ExtractedTask(owner="John", description="Send report", deadline_hint="by Friday",
                      estimated_duration_minutes=30, priority=7)
    ])
    scheduler = FakeSchedulerClient(response={
        "placed": [{"description": "Send report", "owner": "22222222-2222-2222-2222-222222222222",
                    "start": "2026-09-04T00:00:00Z", "end": "2026-09-04T00:30:00Z"}],
        "rejected": [],
        "unresolved": [],
    })
    _override(extraction_service=extraction, scheduler_client=scheduler)

    try:
        response = client.post("/pipeline/process", json={
            "text": "John will send the report by Friday.",
            "recording_timestamp": "2026-09-01T09:00:00Z",
        })

        assert response.status_code == 200
        body = response.json()
        assert len(body["placed"]) == 1
        assert body["placed"][0]["description"] == "Send report"

        # deadline_hint should have been resolved to a concrete ISO instant
        # before being sent to the scheduler engine, not passed through raw
        submitted = scheduler.last_submitted_tasks
        assert len(submitted) == 1
        assert submitted[0]["ownerName"] == "John"
        assert submitted[0]["deadline"] != "by Friday"
        assert submitted[0]["deadline"].startswith("2026-09-04")
    finally:
        app.dependency_overrides.clear()


def test_process_pipeline_rejects_empty_text():
    response = client.post("/pipeline/process", json={"text": "   ", "recording_timestamp": "2026-09-01T09:00:00Z"})
    assert response.status_code == 422


def test_process_pipeline_returns_502_on_malformed_extraction():
    _override(extraction_service=FakeExtractionService(raises=MalformedExtractionError("bad output")))

    try:
        response = client.post("/pipeline/process", json={
            "text": "some transcript",
            "recording_timestamp": "2026-09-01T09:00:00Z",
        })
        assert response.status_code == 502
    finally:
        app.dependency_overrides.clear()


def test_process_pipeline_returns_502_when_scheduler_engine_unreachable():
    extraction = FakeExtractionService(tasks=[
        ExtractedTask(owner="John", description="Send report", deadline_hint="by Friday",
                      estimated_duration_minutes=30, priority=7)
    ])
    scheduler = FakeSchedulerClient(raises=SchedulerClientError("Scheduler engine unreachable after retry"))
    _override(extraction_service=extraction, scheduler_client=scheduler)

    try:
        response = client.post("/pipeline/process", json={
            "text": "some transcript",
            "recording_timestamp": "2026-09-01T09:00:00Z",
        })
        assert response.status_code == 502
    finally:
        app.dependency_overrides.clear()

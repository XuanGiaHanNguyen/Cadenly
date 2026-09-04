from datetime import datetime, timezone

from app.extraction.base import TaskExtractionService
from app.scheduling.deadline_resolver import resolve_deadline
from app.scheduling.scheduler_client import SchedulerClient


def _to_java_instant_string(dt: datetime) -> str:
    """Java's Instant.parse() strictly expects the 'Z' UTC suffix, not a
    numeric offset - normalize explicitly rather than relying on however
    Jackson's jsr310 module happens to handle other ISO-8601 variants."""
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def process_transcript(
    text: str,
    recording_timestamp: datetime,
    extraction_service: TaskExtractionService,
    scheduler_client: SchedulerClient,
) -> dict:
    extracted_tasks = extraction_service.extract(text)

    payload_tasks = [
        {
            "ownerName": task.owner,
            "description": task.description,
            "deadline": _to_java_instant_string(resolve_deadline(task.deadline_hint, recording_timestamp)),
            "priority": task.priority,
            "estimatedDurationMinutes": task.estimated_duration_minutes,
        }
        for task in extracted_tasks
    ]

    return scheduler_client.submit_tasks(payload_tasks)

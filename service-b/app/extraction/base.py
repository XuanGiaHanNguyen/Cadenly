from typing import Protocol

from app.api.schemas import ExtractedTask


class MalformedExtractionError(Exception):
    """Raised when the LLM's tool-use output fails schema validation.
    Callers should treat this as a 502 - the upstream model returned
    something we can't trust, not something to silently coerce."""


class TaskExtractionService(Protocol):
    def extract(self, transcript: str) -> list[ExtractedTask]:
        ...

import json
import os

from openai import OpenAI
from pydantic import ValidationError

from app.api.schemas import ExtractedTask
from app.extraction.base import MalformedExtractionError
from app.extraction.prompt import SYSTEM_PROMPT, build_user_message

_DEFAULT_BASE_URL = "http://localhost:11434/v1"
_DEFAULT_MODEL = "llama3.1:8b"

# Request timeout so a stalled/unreachable local Ollama server fails loudly
# instead of hanging indefinitely (same lesson as the Claude client hang).
# Configurable: Phase 8 load testing found the 30s default is too short for
# longer transcripts (a real, reportable finding, not just a test hack) -
# extraction time scales with transcript length, and a 5+ minute meeting's
# transcript can genuinely take longer than 30s on an 8B CPU model.
_DEFAULT_TIMEOUT_SECONDS = 30.0


def _strip_markdown_fences(text: str) -> str:
    """Formatting cleanup, not content repair: smaller local models
    sometimes wrap JSON in ```json ... ``` despite being told not to.
    Stripping the fence characters doesn't change any actual content."""
    stripped = text.strip()
    if stripped.startswith("```"):
        lines = stripped.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        stripped = "\n".join(lines).strip()
    return stripped


class OllamaTaskExtractionService:
    def __init__(self) -> None:
        self._base_url = os.environ.get("OLLAMA_BASE_URL", _DEFAULT_BASE_URL)
        self._model = os.environ.get("OLLAMA_MODEL", _DEFAULT_MODEL)
        self._timeout_seconds = float(os.environ.get("OLLAMA_TIMEOUT_SECONDS", _DEFAULT_TIMEOUT_SECONDS))

    def extract(self, transcript: str) -> list[ExtractedTask]:
        client = OpenAI(
            base_url=self._base_url,
            api_key="ollama",  # unused by Ollama, but the SDK requires a non-empty value
            timeout=self._timeout_seconds,
        )

        response = client.chat.completions.create(
            model=self._model,
            temperature=0,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_message(transcript)},
            ],
        )

        content = response.choices[0].message.content
        if content is None:
            raise MalformedExtractionError("Ollama response had no message content")

        cleaned = _strip_markdown_fences(content)
        try:
            parsed = json.loads(cleaned)
        except json.JSONDecodeError as e:
            raise MalformedExtractionError(f"Ollama output was not valid JSON: {e}") from e

        raw_tasks = parsed.get("tasks") if isinstance(parsed, dict) else None
        if raw_tasks is None:
            raise MalformedExtractionError("Ollama output missing 'tasks' field")
        if not isinstance(raw_tasks, list):
            raise MalformedExtractionError(f"Ollama output's 'tasks' field was not a list (got {type(raw_tasks).__name__})")

        try:
            return [ExtractedTask.model_validate(t) for t in raw_tasks]
        except ValidationError as e:
            raise MalformedExtractionError(f"Ollama output failed schema validation: {e}") from e

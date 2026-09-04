from typing import Protocol


class TranscriptionService(Protocol):
    """Abstraction over "audio bytes in, transcript text out" so the API
    layer doesn't depend on a specific Whisper backend. Lets the audio
    endpoint be tested with a fake implementation - no model load, no
    audio fixture, no slow test - while faster_whisper_service.py provides
    the real one."""

    def transcribe(self, audio_path: str) -> str:
        ...

from functools import lru_cache

from faster_whisper import WhisperModel

# "base" on CPU with int8 quantization: no GPU required in this environment,
# audio never leaves the machine (privacy for potentially confidential
# meeting content), no per-request API cost or network dependency at
# inference time. See Phase 5 design notes for the full tradeoff table
# against the hosted OpenAI Whisper API.
_MODEL_SIZE = "base"


@lru_cache(maxsize=1)
def _load_model() -> WhisperModel:
    return WhisperModel(_MODEL_SIZE, device="cpu", compute_type="int8")


class FasterWhisperTranscriptionService:
    def transcribe(self, audio_path: str) -> str:
        model = _load_model()
        segments, _info = model.transcribe(audio_path)
        return " ".join(segment.text.strip() for segment in segments).strip()

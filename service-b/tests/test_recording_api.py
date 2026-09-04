import io

from fastapi.testclient import TestClient

from app.api.routes import get_transcription_service
from app.main import app
from tests.fixtures.sample_transcripts import SIGNAL_VS_FILLER


class FakeTranscriptionService:
    """Proves the upload -> transcribe -> summarize -> response wiring
    without loading a real Whisper model or needing an audio fixture."""

    def transcribe(self, audio_path: str) -> str:
        return SIGNAL_VS_FILLER


def _client_with_fake_transcription() -> TestClient:
    app.dependency_overrides[get_transcription_service] = lambda: FakeTranscriptionService()
    return TestClient(app)


def test_recording_endpoint_transcribes_and_summarizes():
    client = _client_with_fake_transcription()
    try:
        response = client.post(
            "/recordings/summarize",
            files={"file": ("meeting.wav", io.BytesIO(b"fake audio bytes"), "audio/wav")},
        )
        assert response.status_code == 200
        body = response.json()
        assert body["transcript"] == SIGNAL_VS_FILLER
        assert 1 <= len(body["summary"]) <= 3
        assert len(body["ranked"]) == 7
    finally:
        app.dependency_overrides.clear()

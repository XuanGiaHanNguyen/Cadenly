import os

import pytest

from app.transcription.faster_whisper_service import FasterWhisperTranscriptionService

FIXTURE_PATH = os.path.join(os.path.dirname(__file__), "fixtures", "sample_meeting.wav")


@pytest.mark.slow
def test_transcribes_spoken_audio_to_roughly_the_right_words():
    # sample_meeting.wav was generated with macOS `say`: "The deploy pipeline
    # is broken. We need to roll back the configuration change." Not a WER
    # benchmark - just proves the real model loads and returns plausible
    # text, kept out of the default fast test run since it downloads and
    # runs an actual model.
    service = FasterWhisperTranscriptionService()

    transcript = service.transcribe(FIXTURE_PATH)

    assert transcript.strip() != ""
    lowered = transcript.lower()
    assert "pipeline" in lowered or "deploy" in lowered
    assert "configuration" in lowered or "change" in lowered

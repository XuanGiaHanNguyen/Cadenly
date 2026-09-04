"""
Phase 8 load test: per-stage pipeline latency (transcribe / summarize /
extract / resolve-deadline) for meetings of varying length. Standalone
script, not a pytest test - its job is producing a report, not asserting
pass/fail (same reasoning as Phase 6's compare_extraction_models.py).

Requires a locally-running Ollama (llama3.1:8b) and macOS's `say`/
`afconvert` (used the same way as Phase 5's sample_meeting.wav fixture) to
generate audio without any network TTS dependency.

Run with:
    python scripts/load_test_pipeline_stages.py
"""

import os
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app.extraction.ollama_task_extraction_service import OllamaTaskExtractionService
from app.scheduling.deadline_resolver import resolve_deadline
from app.summarization.textrank import summarize
from app.transcription.faster_whisper_service import FasterWhisperTranscriptionService

# A rotating pool of checkpoint sentences (status updates, action items with
# varied owners/deadlines, small talk) cycled with a varying suffix to reach
# a target word count - not hand-writing thousands of words, and avoiding
# the extreme repetition that would make transcription/extraction trivially
# easy in a way that wouldn't reflect a real meeting.
_TEMPLATE_SENTENCES = [
    "Good morning everyone, thanks for joining today's sync.",
    "Let's start with an update on the deploy pipeline project, checkpoint {n}.",
    "The deploy pipeline has been stable since the last configuration change.",
    "John, can you send the Q3 report to finance by Friday for checkpoint {n}?",
    "John: Yeah, I'll have that done by Friday.",
    "Sarah, we also need the slides updated before next week's board review, item {n}.",
    "Sarah: Got it, I'll take care of the slides.",
    "On the infrastructure side, checkpoint {n} shows steady progress.",
    "We should also review the database migration plan soon, part {n}.",
    "Priya, can you look into the migration risk assessment sometime this week?",
    "Priya: Sure, I'll take a look and report back.",
    "Nice weather we've been having, hope everyone had a good weekend.",
    "Let's also touch on the office snack budget for checkpoint {n}.",
    "No major blockers to report for this part of the meeting, item {n}.",
    "Alright, that wraps up checkpoint {n}, let's move to the next topic.",
]

# ~175 wpm is a reasonable estimate for macOS `say`'s default rate.
_WORDS_PER_MINUTE = 175

LENGTH_TIERS = [
    ("1 min", 1),
    ("5 min", 5),
    ("15 min", 15),
]


def build_transcript_text(target_minutes: int) -> str:
    target_words = target_minutes * _WORDS_PER_MINUTE
    sentences = []
    word_count = 0
    n = 0
    while word_count < target_words:
        for template in _TEMPLATE_SENTENCES:
            sentence = template.format(n=n)
            sentences.append(sentence)
            word_count += len(sentence.split())
            if word_count >= target_words:
                break
        n += 1
    return " ".join(sentences)


def synthesize_audio(text: str, wav_path: str) -> None:
    with tempfile.NamedTemporaryFile(suffix=".txt", mode="w", delete=False) as txt_file:
        txt_file.write(text)
        txt_path = txt_file.name
    aiff_path = wav_path.replace(".wav", ".aiff")
    try:
        subprocess.run(["say", "-f", txt_path, "-o", aiff_path], check=True, capture_output=True)
        subprocess.run(
            ["afconvert", "-f", "WAVE", "-d", "LEI16@16000", aiff_path, wav_path],
            check=True, capture_output=True,
        )
    finally:
        os.remove(txt_path)
        if os.path.exists(aiff_path):
            os.remove(aiff_path)


def timed(fn):
    start = time.perf_counter()
    result = fn()
    elapsed_ms = (time.perf_counter() - start) * 1000
    return result, elapsed_ms


def main() -> None:
    transcription_service = FasterWhisperTranscriptionService()
    extraction_service = OllamaTaskExtractionService()
    recording_timestamp = datetime.now(timezone.utc)

    rows = []

    for label, minutes in LENGTH_TIERS:
        print(f"\n=== {label} transcript ===")
        script_text = build_transcript_text(minutes)
        word_count = len(script_text.split())
        print(f"  script: {word_count} words")

        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as wav_file:
            wav_path = wav_file.name

        try:
            print("  synthesizing audio...")
            _, synth_ms = timed(lambda: synthesize_audio(script_text, wav_path))
            print(f"  audio synthesized in {synth_ms:.0f}ms (not a pipeline stage, just fixture setup)")

            print("  transcribing...")
            transcript, transcribe_ms = timed(lambda: transcription_service.transcribe(wav_path))
            transcript_words = len(transcript.split())
            print(f"  transcribed {transcript_words} words in {transcribe_ms:.0f}ms")

            print("  summarizing...")
            summary_result, summarize_ms = timed(lambda: summarize(transcript))
            print(f"  summarized in {summarize_ms:.0f}ms")

            print("  extracting tasks (real Ollama call)...")
            extracted_tasks, extract_ms = timed(lambda: extraction_service.extract(transcript))
            print(f"  extracted {len(extracted_tasks)} tasks in {extract_ms:.0f}ms")

            if extracted_tasks:
                def resolve_all():
                    return [resolve_deadline(t.deadline_hint, recording_timestamp) for t in extracted_tasks]

                _, resolve_ms = timed(resolve_all)
            else:
                resolve_ms = 0.0
            print(f"  resolved {len(extracted_tasks)} deadlines in {resolve_ms:.1f}ms")

            total_ms = transcribe_ms + summarize_ms + extract_ms + resolve_ms

            rows.append({
                "label": label,
                "script_words": word_count,
                "transcript_words": transcript_words,
                "transcribe_ms": transcribe_ms,
                "summarize_ms": summarize_ms,
                "extract_ms": extract_ms,
                "resolve_ms": resolve_ms,
                "total_ms": total_ms,
                "tasks_extracted": len(extracted_tasks),
            })
        finally:
            os.remove(wav_path)

    print("\n\n=== Summary ===")
    header = f"{'length':<8}{'words':<8}{'transcribe':<13}{'summarize':<12}{'extract':<10}{'resolve':<10}{'total':<10}{'tasks':<6}"
    print(header)
    for row in rows:
        print(
            f"{row['label']:<8}{row['transcript_words']:<8}"
            f"{row['transcribe_ms']:<13.0f}{row['summarize_ms']:<12.1f}"
            f"{row['extract_ms']:<10.0f}{row['resolve_ms']:<10.2f}"
            f"{row['total_ms']:<10.0f}{row['tasks_extracted']:<6}"
        )


if __name__ == "__main__":
    main()

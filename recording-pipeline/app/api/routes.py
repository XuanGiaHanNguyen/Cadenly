import os
import tempfile

from fastapi import APIRouter, Depends, HTTPException, UploadFile

from app.api.schemas import (
    ExtractTasksRequest,
    ExtractTasksResponse,
    ProcessPipelineRequest,
    ProcessPipelineResponse,
    RankedSentenceResponse,
    RecordingSummarizeResponse,
    SummarizeRequest,
    SummarizeResponse,
)
from app.extraction.base import MalformedExtractionError, TaskExtractionService
from app.extraction.ollama_task_extraction_service import OllamaTaskExtractionService
from app.scheduling.pipeline import process_transcript
from app.scheduling.scheduler_client import SchedulerClient, SchedulerClientError
from app.summarization.textrank import SummaryResult, summarize
from app.transcription.base import TranscriptionService
from app.transcription.faster_whisper_service import FasterWhisperTranscriptionService

router = APIRouter()


def get_transcription_service() -> TranscriptionService:
    return FasterWhisperTranscriptionService()


def get_task_extraction_service() -> TaskExtractionService:
    return OllamaTaskExtractionService()


def get_scheduler_client() -> SchedulerClient:
    return SchedulerClient()


def _to_response(result: SummaryResult) -> tuple[list[str], list[RankedSentenceResponse]]:
    ranked = [RankedSentenceResponse(index=r.index, text=r.text, score=r.score) for r in result.ranked]
    return result.sentences, ranked


@router.post("/summarize", response_model=SummarizeResponse)
def summarize_text(request: SummarizeRequest) -> SummarizeResponse:
    try:
        result = summarize(
            request.text,
            top_k=request.top_k,
            redundancy_suppression=request.redundancy_suppression,
        )
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

    sentences, ranked = _to_response(result)
    return SummarizeResponse(summary=sentences, ranked=ranked)


@router.post("/recordings/summarize", response_model=RecordingSummarizeResponse)
def summarize_recording(
    file: UploadFile,
    transcription_service: TranscriptionService = Depends(get_transcription_service),
) -> RecordingSummarizeResponse:
    suffix = os.path.splitext(file.filename or "")[1] or ".wav"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(file.file.read())
        tmp_path = tmp.name

    try:
        transcript = transcription_service.transcribe(tmp_path)
        try:
            result = summarize(transcript)
        except ValueError as e:
            raise HTTPException(status_code=422, detail=str(e))
    finally:
        os.remove(tmp_path)

    sentences, ranked = _to_response(result)
    return RecordingSummarizeResponse(transcript=transcript, summary=sentences, ranked=ranked)


@router.post("/extract-tasks", response_model=ExtractTasksResponse)
def extract_tasks(
    request: ExtractTasksRequest,
    extraction_service: TaskExtractionService = Depends(get_task_extraction_service),
) -> ExtractTasksResponse:
    if not request.text.strip():
        raise HTTPException(status_code=422, detail="text must not be empty")

    try:
        tasks = extraction_service.extract(request.text)
    except MalformedExtractionError as e:
        raise HTTPException(status_code=502, detail=str(e))

    return ExtractTasksResponse(tasks=tasks)


@router.post("/pipeline/process", response_model=ProcessPipelineResponse)
def process(
    request: ProcessPipelineRequest,
    extraction_service: TaskExtractionService = Depends(get_task_extraction_service),
    scheduler_client: SchedulerClient = Depends(get_scheduler_client),
) -> ProcessPipelineResponse:
    if not request.text.strip():
        raise HTTPException(status_code=422, detail="text must not be empty")

    try:
        result = process_transcript(request.text, request.recording_timestamp, extraction_service, scheduler_client)
    except MalformedExtractionError as e:
        raise HTTPException(status_code=502, detail=f"task extraction failed: {e}")
    except SchedulerClientError as e:
        raise HTTPException(status_code=502, detail=f"scheduler engine task submission failed: {e}")

    return ProcessPipelineResponse.model_validate(result)

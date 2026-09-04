from datetime import datetime

from pydantic import BaseModel, Field


class SummarizeRequest(BaseModel):
    text: str
    top_k: int = 3
    redundancy_suppression: bool = True


class RankedSentenceResponse(BaseModel):
    index: int
    text: str
    score: float


class SummarizeResponse(BaseModel):
    summary: list[str] = Field(description="Selected sentences, in original transcript order")
    ranked: list[RankedSentenceResponse] = Field(description="Every sentence with its PageRank score")


class RecordingSummarizeResponse(BaseModel):
    transcript: str
    summary: list[str]
    ranked: list[RankedSentenceResponse]


class ExtractedTask(BaseModel):
    """Extraction-layer intermediate format - NOT the scheduler engine's Task
    model. owner is a best-effort name string (not a UUID) and deadline_hint
    is a raw, unresolved phrase (not an Instant). Phase 7 resolves both
    against a real user directory and a reference timestamp when serializing
    into the scheduler engine's REST payload; that resolution needs context
    this stateless extraction call doesn't have."""

    owner: str = Field(description="Best-effort name from the transcript, or 'Unassigned'")
    description: str
    deadline_hint: str = Field(description="Raw deadline phrase, e.g. 'by Friday'; empty if none mentioned")
    estimated_duration_minutes: int = Field(ge=1)
    priority: int = Field(ge=1, le=10)


class ExtractTasksRequest(BaseModel):
    text: str


class ExtractTasksResponse(BaseModel):
    tasks: list[ExtractedTask]


class ProcessPipelineRequest(BaseModel):
    text: str
    recording_timestamp: datetime = Field(
        description="Reference timestamp the meeting actually happened at - used to resolve "
        "relative deadline phrases like 'next week', not the time this endpoint is called."
    )


class PlacedTaskResponse(BaseModel):
    description: str
    owner: str
    start: datetime
    end: datetime


class RejectedTaskResponse(BaseModel):
    description: str
    owner: str
    reason: str


class UnresolvedTaskResponse(BaseModel):
    ownerNameRaw: str
    description: str
    reason: str


class ProcessPipelineResponse(BaseModel):
    placed: list[PlacedTaskResponse]
    rejected: list[RejectedTaskResponse]
    unresolved: list[UnresolvedTaskResponse]

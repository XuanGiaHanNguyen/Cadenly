package com.cadenly.scheduler.model;

import java.util.List;

public record TaskSubmissionResponse(
        List<PlacedTaskResponse> placed,
        List<RejectedTaskResponse> rejected,
        List<UnresolvedTaskResponse> unresolved
) {
}

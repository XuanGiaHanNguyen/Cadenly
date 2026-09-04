package com.cadenly.scheduler.model;

/** ownerNameRaw is unchanged from what the recording pipeline sent - never reached the scheduler. */
public record UnresolvedTaskResponse(
        String ownerNameRaw, String description, String reason,
        int priority, int estimatedDurationMinutes
) {
}

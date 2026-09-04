package com.cadenly.scheduler.model;

import java.time.Instant;

/** Wire-level submission payload from the recording pipeline: owner is a raw, unresolved name string. */
public record TaskSubmissionItem(
        String ownerName,
        String description,
        Instant deadline,
        int priority,
        int estimatedDurationMinutes
) {
}

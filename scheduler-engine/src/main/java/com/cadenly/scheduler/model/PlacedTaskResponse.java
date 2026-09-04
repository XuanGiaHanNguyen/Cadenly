package com.cadenly.scheduler.model;

import java.time.Instant;
import java.util.UUID;

public record PlacedTaskResponse(
        UUID id, String description, UUID owner, Instant start, Instant end,
        int priority, int estimatedDurationMinutes
) {
}

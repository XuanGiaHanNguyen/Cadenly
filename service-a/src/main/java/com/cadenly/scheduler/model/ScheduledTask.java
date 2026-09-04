package com.cadenly.scheduler.model;

import java.time.Instant;
import java.util.UUID;

public record ScheduledTask(UUID taskId, UUID userId, Instant start, Instant end, long weight) {
}

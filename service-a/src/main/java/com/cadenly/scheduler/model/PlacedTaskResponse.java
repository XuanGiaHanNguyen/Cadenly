package com.cadenly.scheduler.model;

import java.time.Instant;
import java.util.UUID;

public record PlacedTaskResponse(String description, UUID owner, Instant start, Instant end) {
}

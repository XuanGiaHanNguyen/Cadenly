package com.cadenly.scheduler.model;

import java.time.Instant;
import java.util.UUID;

/** A fixed, immovable commitment already on a user's calendar. */
public record CalendarEvent(UUID id, UUID userId, Instant start, Instant end) {
}

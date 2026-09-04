package com.cadenly.scheduler.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by SharedResourceCalendar after a successful tryBook, never on
 * a rejected one. Plain POJO event (Spring supports arbitrary event types
 * since 4.2) - carries only what tryBook itself knows, no task/owner
 * metadata, so the calendar stays decoupled from anything downstream.
 */
public record ResourceBookedEvent(
        UUID eventId,
        UUID resourceId,
        TimeSlot slot,
        Instant occurredAt
) {
}

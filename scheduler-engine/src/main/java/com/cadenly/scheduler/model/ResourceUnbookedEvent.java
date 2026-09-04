package com.cadenly.scheduler.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by SharedResourceCalendar after a successful unbook. Mirrors
 * ResourceBookedEvent's shape so dashboard clients can reuse the same
 * decoding logic for both; broadcast on a separate topic so existing
 * /topic/bookings subscribers (which only ever expected new bookings)
 * don't need to start distinguishing event types.
 */
public record ResourceUnbookedEvent(
        UUID eventId,
        UUID resourceId,
        TimeSlot slot,
        Instant occurredAt
) {
}

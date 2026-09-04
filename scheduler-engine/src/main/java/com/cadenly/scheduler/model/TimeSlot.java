package com.cadenly.scheduler.model;

import java.time.Duration;
import java.time.Instant;

/**
 * A half-open time window [start, end). Two slots where a.end() == b.start()
 * are adjacent, not overlapping.
 */
public record TimeSlot(Instant start, Instant end) {
    public TimeSlot {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end: " + start + " >= " + end);
        }
    }

    public Duration length() {
        return Duration.between(start, end);
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}

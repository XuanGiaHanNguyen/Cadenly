package com.cadenly.scheduler.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An extracted action item awaiting placement on the owner's calendar.
 * priority is a raw 1-10 input (from the recording pipeline's extraction); the DP's
 * scheduling weight is derived from it, see WeightCalculator.
 */
public record Task(
        UUID id,
        UUID owner,
        String description,
        Instant deadline,
        int priority,
        Duration estimatedDuration
) {
}

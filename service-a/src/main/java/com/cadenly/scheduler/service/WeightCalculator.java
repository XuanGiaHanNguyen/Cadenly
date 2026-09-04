package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Task;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Derives the DP scheduling weight from a task's raw priority (1-10) and its
 * urgency (how close the deadline is).
 *
 * Urgency uses 1 / (1 + hoursRemaining) rather than the naive 1 / hoursRemaining:
 * the naive form has a singularity at hoursRemaining == 0 and diverges for
 * anything within the last hour, letting a single near-deadline task swamp
 * the priority term entirely. The +1 smoothing keeps urgency in a bounded
 * (0, 1] range for every input, including exactly-due and overdue
 * (hoursRemaining clamped to 0) tasks, with no cap/floor hack needed.
 */
@Component
public class WeightCalculator {

    static final double PRIORITY_SCALE = 100.0;
    static final double URGENCY_BONUS_SCALE = 50.0;

    public long weight(Task task, Instant now) {
        double hoursRemaining = Duration.between(now, task.deadline()).toMinutes() / 60.0;
        double clamped = Math.max(0.0, hoursRemaining); // overdue tasks: treat as 0 hours remaining
        double urgency = 1.0 / (1.0 + clamped); // in (0, 1], bounded, no division by zero

        double score = task.priority() * PRIORITY_SCALE + urgency * URGENCY_BONUS_SCALE;
        return Math.round(score);
    }
}

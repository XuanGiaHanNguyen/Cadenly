package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Task;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the "urgency term shouldn't blow up for near-deadline tasks"
 * requirement: weight() uses 1/(1+hoursRemaining) rather than the naive
 * 1/hoursRemaining, so it must stay finite and bounded no matter how close
 * (or how overdue) the deadline is.
 */
class WeightCalculatorTest {

    private final WeightCalculator calculator = new WeightCalculator();
    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");

    private Task taskDueIn(Duration untilDeadline, int priority) {
        return new Task(UUID.randomUUID(), UUID.randomUUID(), "task", now.plus(untilDeadline), priority, Duration.ofMinutes(30));
    }

    @Test
    void weightStaysInSaneRange_evenAsDeadlineApproachesNow() {
        // Regression guard for the naive 1/hoursRemaining formula, which diverges toward
        // Long.MAX_VALUE (via Math.round on Infinity/NaN) as hoursRemaining -> 0.
        long dueInOneSecond = calculator.weight(taskDueIn(Duration.ofSeconds(1), 5), now);
        long dueExactlyNow = calculator.weight(taskDueIn(Duration.ZERO, 5), now);

        assertThat(dueInOneSecond).isBetween(0L, 10_000L);
        assertThat(dueExactlyNow).isBetween(0L, 10_000L);
    }

    @Test
    void weightDoesNotBlowUp_forOverdueTasks() {
        // deadline already 10 hours in the past: naive 1/hoursRemaining would go negative/undefined;
        // clamping to 0 hours remaining means this is bounded exactly like a due-right-now task.
        long overdue = calculator.weight(taskDueIn(Duration.ofHours(-10), 5), now);
        long dueNow = calculator.weight(taskDueIn(Duration.ZERO, 5), now);

        assertThat(overdue).isEqualTo(dueNow);
    }

    @Test
    void weightIsBounded_byPriorityScalePlusUrgencyBonusScale() {
        // Maximum possible weight (urgency == 1) for a given priority is a known, finite ceiling -
        // no near-deadline task can spike past it regardless of how close the deadline is.
        int priority = 10;
        long ceiling = Math.round(priority * WeightCalculator.PRIORITY_SCALE + WeightCalculator.URGENCY_BONUS_SCALE);

        long dueNow = calculator.weight(taskDueIn(Duration.ZERO, priority), now);
        long dueInOneMinute = calculator.weight(taskDueIn(Duration.ofMinutes(1), priority), now);

        assertThat(dueNow).isLessThanOrEqualTo(ceiling);
        assertThat(dueInOneMinute).isLessThanOrEqualTo(ceiling);
    }

    @Test
    void weightDecreasesMonotonically_asDeadlineMovesFurtherOut() {
        int priority = 5;
        long dueIn10Min = calculator.weight(taskDueIn(Duration.ofMinutes(10), priority), now);
        long dueIn1Hour = calculator.weight(taskDueIn(Duration.ofHours(1), priority), now);
        long dueIn1Day = calculator.weight(taskDueIn(Duration.ofDays(1), priority), now);
        long dueIn1Week = calculator.weight(taskDueIn(Duration.ofDays(7), priority), now);

        assertThat(dueIn10Min).isGreaterThanOrEqualTo(dueIn1Hour);
        assertThat(dueIn1Hour).isGreaterThanOrEqualTo(dueIn1Day);
        assertThat(dueIn1Day).isGreaterThanOrEqualTo(dueIn1Week);
    }

    @Test
    void higherPriorityStillDominates_evenWhenLowerPriorityTaskIsMoreUrgent() {
        // A priority-10 task due next week should still outweigh a priority-1 task due in a minute -
        // the urgency bonus is a tie-breaker, not something that lets urgency override priority entirely.
        long highPriorityFarOut = calculator.weight(taskDueIn(Duration.ofDays(7), 10), now);
        long lowPriorityUrgent = calculator.weight(taskDueIn(Duration.ofMinutes(1), 1), now);

        assertThat(highPriorityFarOut).isGreaterThan(lowPriorityUrgent);
    }
}

package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Candidate;
import com.cadenly.scheduler.model.Task;
import com.cadenly.scheduler.model.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reduces each task to exactly one candidate interval: the earliest free
 * slot (before its deadline) that's long enough to hold it. This is what
 * lets task placement be solved by classic Weighted Interval Scheduling
 * (select a max-weight subset of fixed, non-overlapping intervals) instead
 * of a harder multi-choice variant.
 *
 * Phase 9 stretch goal: let a task offer multiple candidate slots (not just
 * earliest-fit) and pick the best one as part of the optimization. That is
 * NOT solvable by this DP's recurrence as-is — it needs either an extra
 * (task x slot) dimension or a DP over intervals grouped by task, since two
 * candidates for the same task don't "overlap" in time but are still
 * mutually exclusive. Out of scope for Phase 2.
 */
@Component
public class CandidateGenerator {

    public Optional<Candidate> candidateFor(Task task, List<TimeSlot> freeSlots, WeightCalculator weightCalculator, Instant now) {
        for (TimeSlot slot : freeSlots) {
            if (!slot.start().isBefore(task.deadline())) {
                break; // freeSlots sorted by start; nothing later can fit before the deadline either
            }
            Instant usableEnd = slot.end().isBefore(task.deadline()) ? slot.end() : task.deadline();
            Duration usable = Duration.between(slot.start(), usableEnd);
            if (!usable.minus(task.estimatedDuration()).isNegative()) {
                TimeSlot interval = new TimeSlot(slot.start(), slot.start().plus(task.estimatedDuration()));
                long weight = weightCalculator.weight(task, now);
                return Optional.of(new Candidate(task, interval, weight));
            }
        }
        return Optional.empty();
    }
}

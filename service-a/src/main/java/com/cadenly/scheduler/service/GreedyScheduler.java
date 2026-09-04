package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Candidate;
import com.cadenly.scheduler.model.ScheduleResult;
import com.cadenly.scheduler.model.ScheduledTask;
import com.cadenly.scheduler.model.Task;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Earliest-deadline-first, first-fit baseline: NOT used in production
 * scheduling. Exists solely so Phase 2 tests can demonstrate the DP beats a
 * naive greedy strategy on total weight scheduled.
 */
@Service
public class GreedyScheduler {

    public ScheduleResult schedule(List<Candidate> candidates) {
        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(c -> c.task().deadline()))
                .toList();

        List<Candidate> selected = new ArrayList<>();
        Instant lastEnd = Instant.MIN;
        long totalWeight = 0;
        for (Candidate c : sorted) {
            if (!c.interval().start().isBefore(lastEnd)) {
                selected.add(c);
                lastEnd = c.interval().end();
                totalWeight += c.weight();
            }
        }

        List<ScheduledTask> placed = selected.stream()
                .map(c -> new ScheduledTask(c.task().id(), c.task().owner(), c.interval().start(), c.interval().end(), c.weight()))
                .toList();

        Set<java.util.UUID> placedTaskIds = new HashSet<>();
        for (Candidate c : selected) {
            placedTaskIds.add(c.task().id());
        }
        List<Task> rejected = candidates.stream()
                .map(Candidate::task)
                .filter(t -> !placedTaskIds.contains(t.id()))
                .toList();

        return new ScheduleResult(placed, rejected, totalWeight);
    }
}

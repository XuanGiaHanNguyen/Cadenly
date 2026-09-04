package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Candidate;
import com.cadenly.scheduler.model.ScheduleResult;
import com.cadenly.scheduler.model.ScheduledTask;
import com.cadenly.scheduler.model.Task;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Classic Weighted Interval Scheduling via dynamic programming: given a set
 * of fixed (already-placed-at-one-slot) candidate intervals, selects the
 * subset with maximum total weight such that no two selected intervals
 * overlap.
 *
 * Compatibility is exclusive-end (a.end() <= b.start() means compatible) so
 * two intervals that touch exactly at a boundary are treated as
 * non-conflicting, consistent with TimeSlot's half-open [start, end)
 * semantics.
 *
 * Complexity: O(n log n) - sorting plus a binary search per interval to find
 * p(i), the latest interval compatible with i.
 */
@Service
public class WeightedIntervalScheduler {

    public ScheduleResult schedule(List<Candidate> candidates) {
        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(c -> c.interval().end()))
                .toList();
        int n = sorted.size();

        int[] p = new int[n]; // p[i] = index (0-based) of latest interval compatible with i, or -1
        for (int i = 0; i < n; i++) {
            p[i] = latestCompatible(sorted, i);
        }

        long[] opt = new long[n + 1]; // opt[i] = best total weight using sorted[0..i-1]
        for (int i = 1; i <= n; i++) {
            Candidate c = sorted.get(i - 1);
            long include = c.weight() + opt[p[i - 1] + 1];
            long exclude = opt[i - 1];
            opt[i] = Math.max(include, exclude);
        }

        List<Candidate> selected = new ArrayList<>();
        int i = n;
        while (i > 0) {
            Candidate c = sorted.get(i - 1);
            long include = c.weight() + opt[p[i - 1] + 1];
            if (include >= opt[i - 1]) {
                selected.add(c);
                i = p[i - 1] + 1;
            } else {
                i--;
            }
        }
        Collections.reverse(selected);

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

        return new ScheduleResult(placed, rejected, opt[n]);
    }

    /** Rightmost index j < i such that sorted[j].interval().end() <= sorted[i].interval().start(). */
    private int latestCompatible(List<Candidate> sorted, int i) {
        Instant target = sorted.get(i).interval().start();
        int lo = 0, hi = i - 1, result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (!sorted.get(mid).interval().end().isAfter(target)) { // end <= target
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }
}

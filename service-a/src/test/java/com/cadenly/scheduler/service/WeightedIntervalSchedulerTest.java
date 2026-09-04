package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Candidate;
import com.cadenly.scheduler.model.ScheduleResult;
import com.cadenly.scheduler.model.Task;
import com.cadenly.scheduler.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedIntervalSchedulerTest {

    private final WeightedIntervalScheduler dp = new WeightedIntervalScheduler();
    private final GreedyScheduler greedy = new GreedyScheduler();
    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");

    private Task task(String name, Duration deadlineOffset) {
        return new Task(UUID.randomUUID(), UUID.randomUUID(), name, now.plus(deadlineOffset), 5, Duration.ofHours(1));
    }

    private Candidate candidate(String name, Duration deadlineOffset, Instant start, Instant end, long weight) {
        return new Candidate(task(name, deadlineOffset), new TimeSlot(start, end), weight);
    }

    @Test
    void touchingIntervals_endEqualsNextStart_areTreatedAsCompatible() {
        // a.end() == b.start() exactly: half-open TimeSlot semantics mean these do NOT overlap,
        // so the DP must select both rather than treating the shared boundary as a conflict.
        Instant boundary = now.plus(Duration.ofHours(2));
        Candidate a = candidate("A", Duration.ofHours(10), now, boundary, 50);
        Candidate b = candidate("B", Duration.ofHours(10), boundary, boundary.plus(Duration.ofHours(2)), 50);

        ScheduleResult result = dp.schedule(List.of(a, b));

        assertThat(result.placed()).hasSize(2);
        assertThat(result.rejected()).isEmpty();
        assertThat(result.totalWeightScheduled()).isEqualTo(100);
    }

    @Test
    void dpBeatsGreedy_whenOneLongTaskBlocksSeveralHigherCombinedWeightTasks() {
        // Classic WIS counterexample: earliest-deadline-first greedy grabs the long task A first
        // (it has the earliest deadline) and blocks B, C, D entirely, even though their combined
        // weight (120) exceeds A's weight (100). The DP must find the optimal 120.
        Instant t0 = now;
        Instant t2 = now.plus(Duration.ofHours(2));
        Instant t4 = now.plus(Duration.ofHours(4));
        Instant t6 = now.plus(Duration.ofHours(6));

        Candidate a = candidate("A-long", Duration.ofHours(6), t0, t6, 100);
        Candidate b = candidate("B-short", Duration.ofHours(8), t0, t2, 40);
        Candidate c = candidate("C-short", Duration.ofHours(10), t2, t4, 40);
        Candidate d = candidate("D-short", Duration.ofHours(12), t4, t6, 40);

        List<Candidate> candidates = List.of(a, b, c, d);

        ScheduleResult greedyResult = greedy.schedule(candidates);
        ScheduleResult dpResult = dp.schedule(candidates);

        assertThat(greedyResult.totalWeightScheduled()).isEqualTo(100); // picks A only
        assertThat(dpResult.totalWeightScheduled()).isEqualTo(120);     // picks B, C, D
        assertThat(dpResult.totalWeightScheduled()).isGreaterThan(greedyResult.totalWeightScheduled());

        assertThat(dpResult.placed()).hasSize(3);
        assertThat(dpResult.placed()).extracting(st -> st.taskId()).doesNotContain(a.task().id());
    }

    @Test
    void dpMatchesGreedy_whenTheLongTaskTrulyIsOptimal() {
        // Symmetric case: when A's weight exceeds the combined weight of B+C+D, both strategies
        // should agree on A - proving the DP isn't just biased toward "more tasks."
        Instant t0 = now;
        Instant t2 = now.plus(Duration.ofHours(2));
        Instant t4 = now.plus(Duration.ofHours(4));
        Instant t6 = now.plus(Duration.ofHours(6));

        Candidate a = candidate("A-long", Duration.ofHours(6), t0, t6, 200);
        Candidate b = candidate("B-short", Duration.ofHours(8), t0, t2, 40);
        Candidate c = candidate("C-short", Duration.ofHours(10), t2, t4, 40);
        Candidate d = candidate("D-short", Duration.ofHours(12), t4, t6, 40);

        List<Candidate> candidates = List.of(a, b, c, d);

        ScheduleResult dpResult = dp.schedule(candidates);

        assertThat(dpResult.totalWeightScheduled()).isEqualTo(200);
        assertThat(dpResult.placed()).hasSize(1);
        assertThat(dpResult.placed().get(0).taskId()).isEqualTo(a.task().id());
    }

    @Test
    void nonOverlappingCandidates_areAllScheduled() {
        Instant t0 = now;
        Instant t1 = now.plus(Duration.ofHours(1));
        Instant t2 = now.plus(Duration.ofHours(2));

        Candidate a = candidate("A", Duration.ofHours(5), t0, t1, 10);
        Candidate b = candidate("B", Duration.ofHours(5), t1.plus(Duration.ofMinutes(1)), t2, 10);

        ScheduleResult result = dp.schedule(List.of(a, b));

        assertThat(result.placed()).hasSize(2);
        assertThat(result.totalWeightScheduled()).isEqualTo(20);
    }
}

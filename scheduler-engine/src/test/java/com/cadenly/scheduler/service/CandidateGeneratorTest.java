package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Candidate;
import com.cadenly.scheduler.model.Task;
import com.cadenly.scheduler.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateGeneratorTest {

    private final CandidateGenerator generator = new CandidateGenerator();
    private final WeightCalculator weightCalculator = new WeightCalculator();
    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");

    private Task task(Duration deadlineOffset, Duration duration) {
        return new Task(UUID.randomUUID(), UUID.randomUUID(), "task", now.plus(deadlineOffset), 5, duration);
    }

    @Test
    void picksEarliestSlotThatFitsBeforeDeadline() {
        List<TimeSlot> freeSlots = List.of(
                new TimeSlot(now, now.plus(Duration.ofMinutes(15))),               // too short
                new TimeSlot(now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2))) // fits
        );
        Task t = task(Duration.ofHours(5), Duration.ofMinutes(30));

        Optional<Candidate> candidate = generator.candidateFor(t, freeSlots, weightCalculator, now);

        assertThat(candidate).isPresent();
        assertThat(candidate.get().interval()).isEqualTo(
                new TimeSlot(now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(30)))
        );
    }

    @Test
    void truncatesUsableSlotAtDeadline_andRejectsIfNotEnoughRoom() {
        List<TimeSlot> freeSlots = List.of(
                new TimeSlot(now, now.plus(Duration.ofHours(3)))
        );
        // deadline lands inside the slot, leaving only 20 minutes of usable room for a 30-minute task
        Task t = task(Duration.ofMinutes(20), Duration.ofMinutes(30));

        Optional<Candidate> candidate = generator.candidateFor(t, freeSlots, weightCalculator, now);

        assertThat(candidate).isEmpty();
    }

    @Test
    void noFeasibleSlot_returnsEmpty() {
        Task t = task(Duration.ofMinutes(10), Duration.ofHours(1));
        Optional<Candidate> candidate = generator.candidateFor(t, List.of(), weightCalculator, now);

        assertThat(candidate).isEmpty();
    }
}

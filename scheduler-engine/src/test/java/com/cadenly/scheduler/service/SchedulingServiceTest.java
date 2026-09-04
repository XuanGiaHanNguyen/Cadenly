package com.cadenly.scheduler.service;

import com.cadenly.scheduler.concurrency.SharedResourceCalendar;
import com.cadenly.scheduler.model.TaskSubmissionItem;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingServiceTest {

    private final UserDirectoryService userDirectory = new UserDirectoryService();
    private final OwnerResolver ownerResolver = new OwnerResolver(userDirectory);
    private final FreeSlotService freeSlotService = new FreeSlotService();
    private final CandidateGenerator candidateGenerator = new CandidateGenerator();
    private final WeightedIntervalScheduler weightedIntervalScheduler = new WeightedIntervalScheduler();
    private final WeightCalculator weightCalculator = new WeightCalculator();
    private final SharedResourceCalendar sharedResourceCalendar = new SharedResourceCalendar();
    private final TaskBoard taskBoard = new TaskBoard();

    private final SchedulingService service = new SchedulingService(
            ownerResolver, freeSlotService, candidateGenerator, weightedIntervalScheduler,
            weightCalculator, sharedResourceCalendar, taskBoard
    );

    private final Instant now = Instant.now();

    private TaskSubmissionItem item(String owner, String description, Instant deadline, int priority, int durationMinutes) {
        return new TaskSubmissionItem(owner, description, deadline, priority, durationMinutes);
    }

    @Test
    void resolvableOwnerWithFreeSlot_isPlaced() {
        TaskSubmissionResponse response = service.submitTasks(List.of(
                item("John", "Do X", now.plus(Duration.ofDays(2)), 5, 30)
        ));

        assertThat(response.placed()).hasSize(1);
        assertThat(response.placed().get(0).owner()).isEqualTo(UserDirectoryService.JOHN_ID);
        assertThat(response.placed().get(0).description()).isEqualTo("Do X");
        assertThat(response.rejected()).isEmpty();
        assertThat(response.unresolved()).isEmpty();
    }

    @Test
    void blocklistedOwnerPhrase_isUnresolved_notSubmittedToScheduler() {
        TaskSubmissionResponse response = service.submitTasks(List.of(
                item("someone", "Fix the thing", now.plus(Duration.ofDays(1)), 5, 30)
        ));

        assertThat(response.placed()).isEmpty();
        assertThat(response.rejected()).isEmpty();
        assertThat(response.unresolved()).hasSize(1);
        assertThat(response.unresolved().get(0).ownerNameRaw()).isEqualTo("someone");
        assertThat(response.unresolved().get(0).reason()).isEqualTo("no owner stated");
    }

    @Test
    void unrecognizedName_isUnresolvedWithDistinctReasonFromBlocklist() {
        TaskSubmissionResponse response = service.submitTasks(List.of(
                item("Bob", "Fix the thing", now.plus(Duration.ofDays(1)), 5, 30)
        ));

        assertThat(response.unresolved()).hasSize(1);
        assertThat(response.unresolved().get(0).reason()).contains("Bob").contains("not found in directory");
    }

    @Test
    void twoNameVariantsForSamePerson_shareOneCalendarAndConflictCorrectly() {
        // "Sarah" and "Sarah Kim" must resolve to the same owner - both tasks
        // land in the same free-slot window and the DP must treat them as
        // genuinely competing for the same person's time, not two different people.
        Instant deadline = now.plus(Duration.ofHours(3));
        TaskSubmissionResponse response = service.submitTasks(List.of(
                item("Sarah", "Task A (higher priority)", deadline, 8, 90),
                item("Sarah Kim", "Task B (lower priority)", deadline, 3, 90)
        ));

        assertThat(response.unresolved()).isEmpty();
        assertThat(response.placed()).hasSize(1);
        assertThat(response.placed().get(0).description()).isEqualTo("Task A (higher priority)");
        assertThat(response.placed().get(0).owner()).isEqualTo(UserDirectoryService.SARAH_ID);

        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).description()).isEqualTo("Task B (lower priority)");
        assertThat(response.rejected().get(0).reason()).contains("not selected by scheduler");
    }

    @Test
    void taskWithNoFeasibleSlotBeforeDeadline_isRejected() {
        // deadline in 5 minutes, task takes an hour - structurally can't fit
        TaskSubmissionResponse response = service.submitTasks(List.of(
                item("John", "Impossible task", now.plus(Duration.ofMinutes(5)), 5, 60)
        ));

        assertThat(response.placed()).isEmpty();
        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).reason()).contains("no free slot available before deadline");
    }
}

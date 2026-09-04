package com.cadenly.scheduler.persistence;

import com.cadenly.scheduler.model.PlacedTaskResponse;
import com.cadenly.scheduler.model.RejectedTaskResponse;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import com.cadenly.scheduler.model.UnresolvedTaskResponse;
import com.cadenly.scheduler.port.TaskStore;
import com.cadenly.scheduler.testsupport.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract proof that JpaTaskStore (production) round-trips the same way TaskBoard (the fast-test fake) always has. */
@Tag("integration")
class JpaTaskStoreTest extends AbstractPostgresIntegrationTest {

    private static final UUID JOHN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222"); // seeded by V6

    @Autowired
    private TaskStore taskStore;

    @Test
    void placedTask_isFindableAndRemovable() {
        Instant now = Instant.now();
        UUID taskId = UUID.randomUUID();
        PlacedTaskResponse placed = new PlacedTaskResponse(taskId, "Write report", JOHN_ID, now, now.plus(Duration.ofMinutes(30)), 5, 30);
        taskStore.recordAll(new TaskSubmissionResponse(List.of(placed), List.of(), List.of()));

        Optional<PlacedTaskResponse> found = taskStore.findPlaced(taskId);
        assertThat(found).isPresent();
        assertThat(found.get().description()).isEqualTo("Write report");

        assertThat(taskStore.removePlaced(taskId)).isTrue();
        assertThat(taskStore.findPlaced(taskId)).isEmpty();
        assertThat(taskStore.removePlaced(taskId)).isFalse(); // already gone
    }

    @Test
    void replacePlaced_updatesTheScheduledWindow() {
        Instant now = Instant.now();
        UUID taskId = UUID.randomUUID();
        PlacedTaskResponse placed = new PlacedTaskResponse(taskId, "Reschedule me", JOHN_ID, now, now.plus(Duration.ofMinutes(30)), 5, 30);
        taskStore.recordAll(new TaskSubmissionResponse(List.of(placed), List.of(), List.of()));

        Instant newStart = now.plus(Duration.ofDays(1));
        Instant newEnd = newStart.plus(Duration.ofMinutes(30));
        taskStore.replacePlaced(new PlacedTaskResponse(taskId, "Reschedule me", JOHN_ID, newStart, newEnd, 5, 30));

        PlacedTaskResponse updated = taskStore.findPlaced(taskId).orElseThrow();
        assertThat(updated.start()).isEqualTo(newStart);
        assertThat(updated.end()).isEqualTo(newEnd);
    }

    @Test
    void rejectedAndUnresolvedTasks_areRecordedAndFilteredByOwnerCorrectly() {
        UUID ownerScopedId = UUID.randomUUID();
        RejectedTaskResponse rejected = new RejectedTaskResponse("Rejected task " + ownerScopedId, JOHN_ID, "no free slot available before deadline", 5, 30);
        UnresolvedTaskResponse unresolved = new UnresolvedTaskResponse("Bob", "Unresolved task " + ownerScopedId, "owner name 'Bob' not found in directory", 5, 30);
        taskStore.recordAll(new TaskSubmissionResponse(List.of(), List.of(rejected), List.of(unresolved)));

        TaskSubmissionResponse forJohn = taskStore.forOwner(JOHN_ID);
        assertThat(forJohn.rejected()).anyMatch(r -> r.description().equals(rejected.description()));
        assertThat(forJohn.unresolved()).isEmpty(); // forOwner never includes unresolved - no owner to scope by

        TaskSubmissionResponse everything = taskStore.all();
        assertThat(everything.unresolved()).anyMatch(u -> u.description().equals(unresolved.description()));
    }
}

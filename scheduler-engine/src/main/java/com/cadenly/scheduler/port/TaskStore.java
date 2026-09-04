package com.cadenly.scheduler.port;

import com.cadenly.scheduler.model.PlacedTaskResponse;
import com.cadenly.scheduler.model.TaskSubmissionResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Record of every task submission outcome. The production implementation
 * (JpaTaskStore) persists to the tasks table; the fast test suite uses the
 * original in-memory TaskBoard, which now implements this port instead of
 * being a standalone production class.
 */
public interface TaskStore {

    void recordAll(TaskSubmissionResponse response);

    TaskSubmissionResponse all();

    /** Placed/rejected tasks belonging to one owner; unresolved tasks never resolved to an owner, so they're excluded. */
    TaskSubmissionResponse forOwner(UUID ownerId);

    Optional<PlacedTaskResponse> findPlaced(UUID taskId);

    boolean removePlaced(UUID taskId);

    void replacePlaced(PlacedTaskResponse updated);
}

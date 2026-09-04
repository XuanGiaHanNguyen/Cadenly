package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.PlacedTaskResponse;
import com.cadenly.scheduler.model.RejectedTaskResponse;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import com.cadenly.scheduler.model.UnresolvedTaskResponse;
import com.cadenly.scheduler.port.TaskStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fast-test fixture: in-memory TaskStore, kept exactly as it behaved before
 * the Phase 10 Postgres migration. Production uses JpaTaskStore instead -
 * this class now only exists to let SchedulingServiceTest run against a
 * TaskStore with zero Spring context and zero database.
 */
public class TaskBoard implements TaskStore {

    private final Map<UUID, PlacedTaskResponse> placed = new LinkedHashMap<>();
    private final List<RejectedTaskResponse> rejected = new CopyOnWriteArrayList<>();
    private final List<UnresolvedTaskResponse> unresolved = new CopyOnWriteArrayList<>();

    @Override
    public synchronized void recordAll(TaskSubmissionResponse response) {
        for (PlacedTaskResponse task : response.placed()) {
            placed.put(task.id(), task);
        }
        rejected.addAll(response.rejected());
        unresolved.addAll(response.unresolved());
    }

    @Override
    public synchronized TaskSubmissionResponse all() {
        return new TaskSubmissionResponse(List.copyOf(placed.values()), List.copyOf(rejected), List.copyOf(unresolved));
    }

    @Override
    public synchronized TaskSubmissionResponse forOwner(UUID ownerId) {
        List<PlacedTaskResponse> ownerPlaced = placed.values().stream()
                .filter(task -> task.owner().equals(ownerId))
                .toList();
        List<RejectedTaskResponse> ownerRejected = rejected.stream()
                .filter(task -> task.owner().equals(ownerId))
                .toList();
        return new TaskSubmissionResponse(ownerPlaced, ownerRejected, List.of());
    }

    @Override
    public synchronized Optional<PlacedTaskResponse> findPlaced(UUID taskId) {
        return Optional.ofNullable(placed.get(taskId));
    }

    @Override
    public synchronized boolean removePlaced(UUID taskId) {
        return placed.remove(taskId) != null;
    }

    @Override
    public synchronized void replacePlaced(PlacedTaskResponse updated) {
        placed.put(updated.id(), updated);
    }
}

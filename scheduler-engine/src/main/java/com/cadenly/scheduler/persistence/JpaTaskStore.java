package com.cadenly.scheduler.persistence;

import com.cadenly.scheduler.model.PlacedTaskResponse;
import com.cadenly.scheduler.model.RejectedTaskResponse;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import com.cadenly.scheduler.model.UnresolvedTaskResponse;
import com.cadenly.scheduler.port.TaskStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Production TaskStore: persists to the tasks table. */
@Component
public class JpaTaskStore implements TaskStore {

    private final TaskJpaRepository taskJpaRepository;

    public JpaTaskStore(TaskJpaRepository taskJpaRepository) {
        this.taskJpaRepository = taskJpaRepository;
    }

    @Override
    @Transactional
    public void recordAll(TaskSubmissionResponse response) {
        for (PlacedTaskResponse p : response.placed()) {
            taskJpaRepository.save(TaskEntity.placed(p.id(), p.owner(), p.description(), p.start(), p.end(),
                    p.priority(), p.estimatedDurationMinutes()));
        }
        for (RejectedTaskResponse r : response.rejected()) {
            taskJpaRepository.save(TaskEntity.rejected(r.owner(), r.description(), r.reason(),
                    r.priority(), r.estimatedDurationMinutes()));
        }
        for (UnresolvedTaskResponse u : response.unresolved()) {
            taskJpaRepository.save(TaskEntity.unresolved(u.ownerNameRaw(), u.description(), u.reason(),
                    u.priority(), u.estimatedDurationMinutes()));
        }
    }

    @Override
    public TaskSubmissionResponse all() {
        List<TaskEntity> entities = taskJpaRepository.findAll();
        return toResponse(entities, true);
    }

    @Override
    public TaskSubmissionResponse forOwner(UUID ownerId) {
        List<TaskEntity> entities = taskJpaRepository.findByOwnerIdAndStatusIn(
                ownerId, List.of(TaskEntity.Status.PLACED, TaskEntity.Status.REJECTED));
        return toResponse(entities, false);
    }

    private TaskSubmissionResponse toResponse(List<TaskEntity> entities, boolean includeUnresolved) {
        List<PlacedTaskResponse> placed = entities.stream()
                .filter(e -> e.getStatus() == TaskEntity.Status.PLACED)
                .map(JpaTaskStore::toPlaced)
                .toList();
        List<RejectedTaskResponse> rejected = entities.stream()
                .filter(e -> e.getStatus() == TaskEntity.Status.REJECTED)
                .map(e -> new RejectedTaskResponse(e.getDescription(), e.getOwnerId(), e.getReason(), e.getPriority(), e.getEstimatedDurationMinutes()))
                .toList();
        List<UnresolvedTaskResponse> unresolved = includeUnresolved
                ? entities.stream()
                        .filter(e -> e.getStatus() == TaskEntity.Status.UNRESOLVED)
                        .map(e -> new UnresolvedTaskResponse(e.getOwnerNameRaw(), e.getDescription(), e.getReason(), e.getPriority(), e.getEstimatedDurationMinutes()))
                        .toList()
                : List.of();
        return new TaskSubmissionResponse(placed, rejected, unresolved);
    }

    private static PlacedTaskResponse toPlaced(TaskEntity e) {
        return new PlacedTaskResponse(e.getId(), e.getDescription(), e.getOwnerId(), e.getScheduledStart(), e.getScheduledEnd(),
                e.getPriority(), e.getEstimatedDurationMinutes());
    }

    @Override
    public Optional<PlacedTaskResponse> findPlaced(UUID taskId) {
        return taskJpaRepository.findById(taskId)
                .filter(e -> e.getStatus() == TaskEntity.Status.PLACED)
                .map(JpaTaskStore::toPlaced);
    }

    @Override
    @Transactional
    public boolean removePlaced(UUID taskId) {
        Optional<TaskEntity> existing = taskJpaRepository.findById(taskId)
                .filter(e -> e.getStatus() == TaskEntity.Status.PLACED);
        existing.ifPresent(taskJpaRepository::delete);
        return existing.isPresent();
    }

    @Override
    @Transactional
    public void replacePlaced(PlacedTaskResponse updated) {
        taskJpaRepository.findById(updated.id())
                .filter(e -> e.getStatus() == TaskEntity.Status.PLACED)
                .ifPresent(e -> {
                    e.setScheduledStart(updated.start());
                    e.setScheduledEnd(updated.end());
                    taskJpaRepository.save(e);
                });
    }
}

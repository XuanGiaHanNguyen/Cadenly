package com.cadenly.scheduler.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findByStatus(TaskEntity.Status status);
    List<TaskEntity> findByOwnerIdAndStatusIn(UUID ownerId, List<TaskEntity.Status> statuses);
}

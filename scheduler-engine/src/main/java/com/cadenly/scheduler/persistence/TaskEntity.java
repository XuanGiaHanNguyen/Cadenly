package com.cadenly.scheduler.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Id is application-assigned (the Task domain object's own UUID, generated
 * in SchedulingService), not database-generated - a placed task's id must
 * stay stable across the DTO/PlacedTaskResponse boundary so TaskController
 * can address it later for cancel/reschedule. save() uses merge semantics
 * (an existence check before insert) rather than a bare persist() - one
 * extra SELECT per new row, an acceptable tradeoff for not having to
 * implement Persistable<UUID> for a demo-scale, non-hot-path table.
 */
@Entity
@Table(name = "tasks")
public class TaskEntity {

    public enum Status { PLACED, REJECTED, UNRESOLVED }

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "owner_name_raw")
    private String ownerNameRaw;

    @Column(nullable = false)
    private String description;

    private Instant deadline;

    @Column(nullable = false)
    private int priority;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String reason;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskEntity() {
    }

    public static TaskEntity placed(UUID id, UUID ownerId, String description, Instant scheduledStart, Instant scheduledEnd,
                                     int priority, int estimatedDurationMinutes) {
        TaskEntity entity = new TaskEntity();
        entity.id = id;
        entity.ownerId = ownerId;
        entity.description = description;
        entity.priority = priority;
        entity.estimatedDurationMinutes = estimatedDurationMinutes;
        entity.status = Status.PLACED;
        entity.scheduledStart = scheduledStart;
        entity.scheduledEnd = scheduledEnd;
        entity.createdAt = Instant.now();
        return entity;
    }

    public static TaskEntity rejected(UUID ownerId, String description, String reason,
                                       int priority, int estimatedDurationMinutes) {
        TaskEntity entity = new TaskEntity();
        entity.id = UUID.randomUUID();
        entity.ownerId = ownerId;
        entity.description = description;
        entity.priority = priority;
        entity.estimatedDurationMinutes = estimatedDurationMinutes;
        entity.status = Status.REJECTED;
        entity.reason = reason;
        entity.createdAt = Instant.now();
        return entity;
    }

    public static TaskEntity unresolved(String ownerNameRaw, String description, String reason,
                                         int priority, int estimatedDurationMinutes) {
        TaskEntity entity = new TaskEntity();
        entity.id = UUID.randomUUID();
        entity.ownerNameRaw = ownerNameRaw;
        entity.description = description;
        entity.priority = priority;
        entity.estimatedDurationMinutes = estimatedDurationMinutes;
        entity.status = Status.UNRESOLVED;
        entity.reason = reason;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerNameRaw() {
        return ownerNameRaw;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public int getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledStart(Instant scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public void setScheduledEnd(Instant scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }
}

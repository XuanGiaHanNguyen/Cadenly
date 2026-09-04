package com.cadenly.scheduler.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Replaces SharedResourceCalendar's in-memory ConcurrentHashMap<UUID,
 * List<TimeSlot>>. resource_id stays dual-purpose exactly as before the
 * migration: an owner's personal calendar OR an arbitrary shared resource
 * (e.g. a room). The bookings_no_overlap EXCLUDE constraint (see
 * V5__create_bookings.sql) is the single source of truth preventing
 * double-booking - see JpaBookingStore and Phase 10 design notes.
 */
@Entity
@Table(name = "bookings")
public class BookingEntity {

    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookingEntity() {
    }

    public BookingEntity(UUID resourceId, Instant startAt, Instant endAt) {
        this.id = UUID.randomUUID();
        this.resourceId = resourceId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }
}

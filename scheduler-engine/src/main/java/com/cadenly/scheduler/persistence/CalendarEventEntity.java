package com.cadenly.scheduler.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A fixed, immovable pre-existing commitment on a user's calendar. Real
 * table as of the Phase 10 migration; not yet read by SchedulingService,
 * which still derives busy time from bookings - this is here so the
 * schema has a genuine home for it, not a speculative addition.
 */
@Entity
@Table(name = "calendar_events")
public class CalendarEventEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarEventEntity() {
    }

    public CalendarEventEntity(UUID userId, Instant startAt, Instant endAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }
}

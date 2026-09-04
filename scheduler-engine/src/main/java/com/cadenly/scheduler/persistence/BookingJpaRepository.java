package com.cadenly.scheduler.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, UUID> {
    List<BookingEntity> findByResourceId(UUID resourceId);

    /** Spring Data derives an implicit @Modifying/@Transactional delete from the deleteBy... naming convention. */
    long deleteByResourceIdAndStartAtAndEndAt(UUID resourceId, Instant startAt, Instant endAt);
}

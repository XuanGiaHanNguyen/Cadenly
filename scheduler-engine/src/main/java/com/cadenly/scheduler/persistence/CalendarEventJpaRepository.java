package com.cadenly.scheduler.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CalendarEventJpaRepository extends JpaRepository<CalendarEventEntity, UUID> {
}

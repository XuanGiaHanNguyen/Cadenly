package com.cadenly.scheduler.persistence;

import com.cadenly.scheduler.model.ResourceBookedEvent;
import com.cadenly.scheduler.model.ResourceUnbookedEvent;
import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.port.BookingStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Production BookingStore. Double-booking prevention is delegated entirely
 * to the bookings_no_overlap EXCLUDE constraint (see
 * V5__create_bookings.sql) - there is deliberately no application-level
 * locking here. tryBook attempts a plain INSERT; a caught
 * DataIntegrityViolationException means the constraint rejected an
 * overlapping range, which this class reports the same way
 * SharedResourceCalendar always reported a lost race: a plain boolean
 * false, no exception escaping to the caller. See Phase 10 design notes
 * for the full reasoning (why a database constraint replaces the
 * in-memory per-resource ReentrantLock, and why that's stronger than
 * SELECT ... FOR UPDATE or SERIALIZABLE isolation for this exact problem).
 *
 * Publishes the same ResourceBookedEvent/ResourceUnbookedEvent
 * SharedResourceCalendar always published, so the live STOMP broadcast
 * (BookingBroadcastListener) keeps working unchanged in production.
 */
@Component
public class JpaBookingStore implements BookingStore {

    private final BookingJpaRepository bookingJpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JpaBookingStore(BookingJpaRepository bookingJpaRepository, ApplicationEventPublisher eventPublisher) {
        this.bookingJpaRepository = bookingJpaRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Deliberately not @Transactional here: saveAndFlush is Spring Data's
     * own self-contained transaction (begin, flush, commit-or-rollback, all
     * within that one call). If this method carried its own outer
     * @Transactional instead, catching the constraint violation and
     * returning normally could still surface an UnexpectedRollbackException
     * when Spring tries to commit a transaction Hibernate already marked
     * rollback-only after the failed flush - a well-known trap for exactly
     * this "catch a constraint violation and keep going" pattern. Letting
     * saveAndFlush own its transaction means a failure is fully rolled back
     * and finished by the time the exception reaches this catch, with
     * nothing left to poison.
     */
    @Override
    public boolean tryBook(UUID resourceId, TimeSlot requested) {
        BookingEntity entity = new BookingEntity(resourceId, requested.start(), requested.end());
        try {
            bookingJpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // bookings_no_overlap rejected an overlapping range for this resource - a genuine
            // lost race, reported as a plain rejection, not an error (mirrors SharedResourceCalendar).
            return false;
        }
        eventPublisher.publishEvent(new ResourceBookedEvent(UUID.randomUUID(), resourceId, requested, Instant.now()));
        return true;
    }

    /**
     * @Transactional here (unlike tryBook): a derived deleteBy... query
     * method doesn't get an implicit per-call transaction the way base
     * CrudRepository methods (save, delete, deleteById) do - it needs one
     * explicitly, or Hibernate throws "No EntityManager with actual
     * transaction available". No exception is caught inside this method,
     * so none of tryBook's poisoned-transaction concern applies here.
     */
    @Override
    @Transactional
    public boolean unbook(UUID resourceId, TimeSlot slot) {
        long deleted = bookingJpaRepository.deleteByResourceIdAndStartAtAndEndAt(resourceId, slot.start(), slot.end());
        boolean removed = deleted > 0;
        if (removed) {
            eventPublisher.publishEvent(new ResourceUnbookedEvent(UUID.randomUUID(), resourceId, slot, Instant.now()));
        }
        return removed;
    }

    @Override
    public List<TimeSlot> bookingsFor(UUID resourceId) {
        return bookingJpaRepository.findByResourceId(resourceId).stream()
                .map(e -> new TimeSlot(e.getStartAt(), e.getEndAt()))
                .toList();
    }
}

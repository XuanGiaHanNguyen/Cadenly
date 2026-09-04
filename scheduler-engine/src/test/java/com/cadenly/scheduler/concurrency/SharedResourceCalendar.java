package com.cadenly.scheduler.concurrency;

import com.cadenly.scheduler.model.ResourceBookedEvent;
import com.cadenly.scheduler.model.ResourceUnbookedEvent;
import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.port.BookingStore;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Concurrency case study, not production code as of the Phase 10 Postgres
 * migration: production booking now goes through JpaBookingStore, which
 * delegates double-booking prevention entirely to a database EXCLUDE
 * constraint (see Phase 10 design notes for the full reasoning). This class
 * is kept, unmodified in behavior, purely because it's real, benchmarked,
 * working code demonstrating a genuine tradeoff (see
 * ConcurrencyBenchmarkTest) - deleting it would erase a documented,
 * defensible design decision for zero benefit. It now also doubles as the
 * fast test suite's BookingStore fixture (see SchedulingServiceTest),
 * since its in-memory semantics are still exactly correct for a unit test.
 *
 * tryBook is check-then-act (is the slot free? then add it) which is not
 * atomic on its own - this class makes it atomic with one ReentrantLock per
 * resource, not a single global lock, so bookings against different
 * resources never block each other.
 */
public class SharedResourceCalendar implements BookingStore {

    private final ConcurrentHashMap<UUID, List<TimeSlot>> bookings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public SharedResourceCalendar() {
        this(event -> { }); // no-op publisher: fine for a test fixture with nothing listening
    }

    public SharedResourceCalendar(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean tryBook(UUID resourceId, TimeSlot requested) {
        boolean success = doBook(resourceId, requested);
        if (success) {
            eventPublisher.publishEvent(new ResourceBookedEvent(UUID.randomUUID(), resourceId, requested, Instant.now()));
        }
        return success;
    }

    private boolean doBook(UUID resourceId, TimeSlot requested) {
        ReentrantLock lock = locks.computeIfAbsent(resourceId, id -> new ReentrantLock());
        lock.lock();
        try {
            List<TimeSlot> existing = bookings.computeIfAbsent(resourceId, id -> new ArrayList<>());
            for (TimeSlot slot : existing) {
                if (slot.overlaps(requested)) {
                    return false;
                }
            }
            existing.add(requested);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TimeSlot> bookingsFor(UUID resourceId) {
        return List.copyOf(bookings.getOrDefault(resourceId, List.of()));
    }

    @Override
    public boolean unbook(UUID resourceId, TimeSlot slot) {
        ReentrantLock lock = locks.computeIfAbsent(resourceId, id -> new ReentrantLock());
        boolean removed;
        lock.lock();
        try {
            List<TimeSlot> existing = bookings.get(resourceId);
            removed = existing != null && existing.remove(slot);
        } finally {
            lock.unlock();
        }
        if (removed) {
            eventPublisher.publishEvent(new ResourceUnbookedEvent(UUID.randomUUID(), resourceId, slot, Instant.now()));
        }
        return removed;
    }
}

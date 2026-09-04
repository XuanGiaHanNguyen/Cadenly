package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.ResourceBookedEvent;
import com.cadenly.scheduler.model.TimeSlot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Live booking state for shared resources (a meeting room, or any calendar
 * multiple concurrent requests can contend for). tryBook is check-then-act
 * (is the slot free? then add it) which is not atomic on its own - this
 * class makes it atomic with one ReentrantLock per resource, not a single
 * global lock, so bookings against different resources never block each
 * other. Each tryBook call touches exactly one resource's lock, so there's
 * no multi-lock acquisition and therefore no lock-ordering/deadlock concern.
 *
 * On success, publishes a ResourceBookedEvent via ApplicationEventPublisher
 * - a plain Spring interface with no WebSocket/STOMP knowledge - so this
 * class stays decoupled from broadcast logic entirely. The event is
 * published after the lock is released, so listener work never extends the
 * critical section.
 */
@Service
public class SharedResourceCalendar {

    private final ConcurrentHashMap<UUID, List<TimeSlot>> bookings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public SharedResourceCalendar() {
        this(event -> { }); // no-op publisher: keeps unit tests that do `new SharedResourceCalendar()` working unchanged
    }

    @Autowired
    public SharedResourceCalendar(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

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

    public List<TimeSlot> bookingsFor(UUID resourceId) {
        return List.copyOf(bookings.getOrDefault(resourceId, List.of()));
    }
}

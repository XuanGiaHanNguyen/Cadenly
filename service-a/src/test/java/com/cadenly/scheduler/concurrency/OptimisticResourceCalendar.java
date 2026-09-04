package com.cadenly.scheduler.concurrency;

import com.cadenly.scheduler.model.TimeSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comparison implementation of SharedResourceCalendar using optimistic
 * concurrency instead of a lock: an immutable, version-stamped snapshot per
 * resource, updated via compare-and-swap. On a lost CAS race, the attempt
 * re-reads the latest state and retries rather than blocking.
 *
 * Minimal/benchmark-grade, not production: retries are uncapped in spirit
 * but bounded by MAX_RETRIES as a livelock guard, and totalRetries() exists
 * purely to quantify wasted work for the pessimistic-vs-optimistic
 * comparison, not for real observability.
 */
public class OptimisticResourceCalendar {

    private record VersionedBookings(long version, List<TimeSlot> slots) {
    }

    private static final int MAX_RETRIES = 100_000;

    private final Map<UUID, AtomicReference<VersionedBookings>> states = new ConcurrentHashMap<>();
    private final AtomicLong totalRetries = new AtomicLong();

    public boolean tryBook(UUID resourceId, TimeSlot requested) {
        AtomicReference<VersionedBookings> ref = states.computeIfAbsent(
                resourceId, id -> new AtomicReference<>(new VersionedBookings(0, List.of())));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            VersionedBookings current = ref.get();
            boolean conflict = current.slots().stream().anyMatch(slot -> slot.overlaps(requested));
            if (conflict) {
                return false;
            }

            List<TimeSlot> updated = new ArrayList<>(current.slots());
            updated.add(requested);
            VersionedBookings next = new VersionedBookings(current.version() + 1, List.copyOf(updated));

            if (ref.compareAndSet(current, next)) {
                return true;
            }
            // Lost the race to another writer between our read and our CAS: someone else's
            // update landed first, so our computed `next` is stale. Re-read and retry.
            totalRetries.incrementAndGet();
        }
        throw new IllegalStateException("Exceeded max CAS retries for resource " + resourceId);
    }

    public List<TimeSlot> bookingsFor(UUID resourceId) {
        AtomicReference<VersionedBookings> ref = states.get(resourceId);
        return ref == null ? List.of() : ref.get().slots();
    }

    public long totalRetries() {
        return totalRetries.get();
    }
}

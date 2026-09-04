package com.cadenly.scheduler.concurrency;

import com.cadenly.scheduler.model.TimeSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberately broken "before" version of SharedResourceCalendar: the same
 * check-then-act logic with no locking. A small sleep is inserted between
 * the check and the write to widen the race window so the double-booking
 * reproduces reliably under concurrent load instead of depending on
 * unlucky scheduler timing. Test-only - never use this for real bookings.
 */
public class UnsafeSharedResourceCalendar {

    private final Map<UUID, List<TimeSlot>> bookings = new HashMap<>();

    /**
     * Pre-populates an empty booking list for a resource, called
     * single-threaded before concurrent load starts (see RaceConditionTest).
     * A plain HashMap's first insert for a new key is itself an unsynchronized
     * structural modification - a genuine hazard, but incidental to and
     * separate from the check-then-act race this class exists to demonstrate.
     * Seeding ahead of time means concurrent tryBook calls only ever hit
     * HashMap's already-present read path, isolating the demo to the
     * intended race instead of also (nondeterministically) tripping over
     * this unrelated one.
     */
    public void seedResource(UUID resourceId) {
        bookings.computeIfAbsent(resourceId, id -> new ArrayList<>());
    }

    public boolean tryBook(UUID resourceId, TimeSlot requested) {
        List<TimeSlot> existing = bookings.computeIfAbsent(resourceId, id -> new ArrayList<>());
        boolean conflict = existing.stream().anyMatch(slot -> slot.overlaps(requested));
        if (conflict) {
            return false;
        }
        widenRaceWindow();
        existing.add(requested);
        return true;
    }

    private void widenRaceWindow() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<TimeSlot> bookingsFor(UUID resourceId) {
        return bookings.getOrDefault(resourceId, List.of());
    }
}

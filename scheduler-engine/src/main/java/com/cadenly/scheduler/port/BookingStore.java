package com.cadenly.scheduler.port;

import com.cadenly.scheduler.model.TimeSlot;

import java.util.List;
import java.util.UUID;

/**
 * Booking state for shared resources (a meeting room, or any calendar
 * multiple concurrent requests can contend for).
 *
 * The production implementation (JpaBookingStore) delegates double-booking
 * prevention entirely to a Postgres EXCLUDE constraint on (resource_id,
 * slot) - see Phase 10 design notes for why that replaces per-resource
 * application locking. The fast test suite uses the original in-memory
 * SharedResourceCalendar (per-resource ReentrantLock), which now
 * implements this port instead of being a standalone production class -
 * see com.cadenly.scheduler.concurrency for it and its case-study tests.
 */
public interface BookingStore {

    boolean tryBook(UUID resourceId, TimeSlot requested);

    boolean unbook(UUID resourceId, TimeSlot slot);

    List<TimeSlot> bookingsFor(UUID resourceId);
}

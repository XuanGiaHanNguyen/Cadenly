package com.cadenly.scheduler.concurrency;

import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.service.SharedResourceCalendar;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the race condition exists without locking, then proves the same
 * concurrent-load harness no longer produces it once SharedResourceCalendar's
 * per-resource ReentrantLock is in place.
 */
class RaceConditionTest {

    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");
    private final TimeSlot slot = new TimeSlot(now, now.plus(Duration.ofHours(1)));

    @Test
    void unsafeCalendar_underConcurrentBooking_producesDoubleBooking() throws InterruptedException {
        UnsafeSharedResourceCalendar calendar = new UnsafeSharedResourceCalendar();
        UUID resourceId = UUID.randomUUID();
        // Isolates the demo to the check-then-act race on the booking list -
        // without this, concurrent threads also race HashMap's own unsynchronized
        // first-insert for this key, which is a separate, incidental hazard that
        // can throw ConcurrentModificationException instead of double-booking.
        calendar.seedResource(resourceId);
        int threads = 10;

        ConcurrencyHarness.Result result = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> calendar.tryBook(resourceId, slot));

        assertThat(result.successCount())
                .as("check-then-act with no lock lets multiple threads both see 'free' before either writes")
                .isGreaterThan(1);
        assertThat(calendar.bookingsFor(resourceId))
                .as("corrupted state: more than one overlapping booking was accepted for the same resource")
                .hasSizeGreaterThan(1);
    }

    @Test
    void lockedCalendar_underSameConcurrentLoad_preventsDoubleBooking() throws InterruptedException {
        SharedResourceCalendar calendar = new SharedResourceCalendar();
        UUID resourceId = UUID.randomUUID();
        int threads = 10;

        ConcurrencyHarness.Result result = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> calendar.tryBook(resourceId, slot));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(calendar.bookingsFor(resourceId)).hasSize(1);
    }

    @Test
    void lockedCalendar_differentResources_bookConcurrentlyWithoutInterference() throws InterruptedException {
        SharedResourceCalendar calendar = new SharedResourceCalendar();
        UUID resourceA = UUID.randomUUID();
        UUID resourceB = UUID.randomUUID();

        ConcurrencyHarness.Result result = ConcurrencyHarness.runConcurrently(
                2, i -> () -> calendar.tryBook(i == 0 ? resourceA : resourceB, slot));

        assertThat(result.successCount())
                .as("per-resource locking must not serialize bookings against unrelated resources")
                .isEqualTo(2);
        assertThat(calendar.bookingsFor(resourceA)).hasSize(1);
        assertThat(calendar.bookingsFor(resourceB)).hasSize(1);
    }
}

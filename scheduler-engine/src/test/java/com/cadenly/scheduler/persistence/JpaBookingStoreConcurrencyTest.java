package com.cadenly.scheduler.persistence;

import com.cadenly.scheduler.concurrency.ConcurrencyHarness;
import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.port.BookingStore;
import com.cadenly.scheduler.testsupport.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Phase 10 successor to RaceConditionTest's production-correctness
 * claim: proves the bookings_no_overlap Postgres EXCLUDE constraint (see
 * V5__create_bookings.sql), not an in-memory lock, is what now prevents
 * double-booking against the real production BookingStore. Same harness,
 * same shape of assertion, same result - exactly one concurrent booking
 * wins - just against a real database instead of a ConcurrentHashMap +
 * ReentrantLock. See Phase 10 design notes for the full reasoning on why
 * the constraint replaces the lock; RaceConditionTest is unchanged and
 * still passes, now proving SharedResourceCalendar (a case-study fixture,
 * not production code) is internally correct rather than proving anything
 * about what production actually does.
 */
@Tag("integration")
class JpaBookingStoreConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BookingStore bookingStore;

    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");

    @Test
    void concurrentBookingsForSameOverlappingSlot_exactlyOneSucceeds() throws InterruptedException {
        UUID resourceId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(now, now.plus(Duration.ofHours(1)));
        int threads = 10;

        ConcurrencyHarness.Result result = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> bookingStore.tryBook(resourceId, slot));

        assertThat(result.successCount())
                .as("bookings_no_overlap must let exactly one concurrent INSERT win, same as RaceConditionTest's in-memory proof")
                .isEqualTo(1);
        assertThat(bookingStore.bookingsFor(resourceId)).hasSize(1);
    }

    @Test
    void concurrentBookingsForDistinctResources_bothSucceedWithoutInterference() throws InterruptedException {
        UUID resourceA = UUID.randomUUID();
        UUID resourceB = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(now, now.plus(Duration.ofHours(1)));

        ConcurrencyHarness.Result result = ConcurrencyHarness.runConcurrently(
                2, i -> () -> bookingStore.tryBook(i == 0 ? resourceA : resourceB, slot));

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(bookingStore.bookingsFor(resourceA)).hasSize(1);
        assertThat(bookingStore.bookingsFor(resourceB)).hasSize(1);
    }

    @Test
    void unbookThenRebook_freesTheSlotForReuse() {
        UUID resourceId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(now, now.plus(Duration.ofHours(1)));

        assertThat(bookingStore.tryBook(resourceId, slot)).isTrue();
        assertThat(bookingStore.tryBook(resourceId, slot)).isFalse(); // still occupied
        assertThat(bookingStore.unbook(resourceId, slot)).isTrue();
        assertThat(bookingStore.tryBook(resourceId, slot)).isTrue(); // free again
    }
}

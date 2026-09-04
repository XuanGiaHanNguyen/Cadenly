package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.ResourceBookedEvent;
import com.cadenly.scheduler.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies event publication in isolation: a fake ApplicationEventPublisher
 * that just records what it's given, no Spring context, no WebSocket, no
 * mocking anything broadcast-related.
 */
class SharedResourceCalendarEventTest {

    private final List<Object> published = new ArrayList<>();
    private final SharedResourceCalendar calendar = new SharedResourceCalendar(published::add);
    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");

    @Test
    void successfulBooking_publishesResourceBookedEvent() {
        UUID resourceId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(now, now.plus(Duration.ofHours(1)));

        boolean success = calendar.tryBook(resourceId, slot);

        assertThat(success).isTrue();
        assertThat(published).hasSize(1);
        assertThat(published.get(0)).isInstanceOf(ResourceBookedEvent.class);

        ResourceBookedEvent event = (ResourceBookedEvent) published.get(0);
        assertThat(event.resourceId()).isEqualTo(resourceId);
        assertThat(event.slot()).isEqualTo(slot);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void rejectedBooking_doesNotPublishAnyEvent() {
        UUID resourceId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(now, now.plus(Duration.ofHours(1)));
        calendar.tryBook(resourceId, slot); // occupies the slot
        published.clear();

        boolean secondAttempt = calendar.tryBook(resourceId, slot); // overlaps -> rejected

        assertThat(secondAttempt).isFalse();
        assertThat(published).isEmpty();
    }
}

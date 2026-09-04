package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.ResourceBookedEvent;
import com.cadenly.scheduler.model.TimeSlot;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Proves the listener's own wiring (event in -> broadcast call out) independent of any real socket. */
class BookingBroadcastListenerTest {

    @Test
    void onResourceBooked_broadcastsToBookingsTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        BookingBroadcastListener listener = new BookingBroadcastListener(messagingTemplate);

        Instant now = Instant.parse("2026-09-03T09:00:00Z");
        ResourceBookedEvent event = new ResourceBookedEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                new TimeSlot(now, now.plus(Duration.ofHours(1))), now);

        listener.onResourceBooked(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/bookings"), eq(event));
    }
}

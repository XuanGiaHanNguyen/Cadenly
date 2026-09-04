package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.ResourceBookedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bridges ResourceBookedEvent (a plain domain event, no WebSocket
 * knowledge) to the STOMP broker. SharedResourceCalendar never references
 * this class or SimpMessagingTemplate directly.
 *
 * @EventListener dispatches synchronously, same thread as the publisher
 * (Spring's default) - so the REST request that triggered the booking
 * blocks until this listener returns, meaning booking latency currently
 * includes broadcast latency. That's fine for now (a STOMP send is fast
 * and this trivially satisfies the ~100ms broadcast target), but it's the
 * seam for @Async later if a slow/backed-up broker ever needs to stop
 * blocking the booking path.
 */
@Component
public class BookingBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    public BookingBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onResourceBooked(ResourceBookedEvent event) {
        messagingTemplate.convertAndSend("/topic/bookings", event);
    }
}

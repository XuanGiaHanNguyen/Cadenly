package com.cadenly.scheduler.web;

import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.service.SharedResourceCalendar;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal entry point so something reachable from a browser can trigger a
 * booking - this is also a preview of the Phase 7 recording-pipeline-calls-
 * scheduler-engine path, just invoked manually here instead of from the AI pipeline.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class BookingController {

    public record BookingRequest(Instant start, Instant end) {
    }

    public record BookingResponse(boolean success) {
    }

    private final SharedResourceCalendar calendar;

    public BookingController(SharedResourceCalendar calendar) {
        this.calendar = calendar;
    }

    @PostMapping("/api/resources/{resourceId}/bookings")
    public ResponseEntity<BookingResponse> book(@PathVariable UUID resourceId, @RequestBody BookingRequest request) {
        boolean success = calendar.tryBook(resourceId, new TimeSlot(request.start(), request.end()));
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new BookingResponse(success));
    }
}

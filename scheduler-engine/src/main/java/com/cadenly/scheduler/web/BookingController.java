package com.cadenly.scheduler.web;

import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.port.BookingStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entry point for raw resource bookings, independent of the task-submission
 * pipeline - this is also a preview of the recording-pipeline-calls-
 * scheduler-engine path, just invoked manually here. Requires an
 * authenticated session (see SecurityConfig); CORS is configured centrally
 * there too, not per controller.
 */
@RestController
public class BookingController {

    public record BookingRequest(Instant start, Instant end) {
    }

    public record BookingResponse(boolean success) {
    }

    private final BookingStore calendar;

    public BookingController(BookingStore calendar) {
        this.calendar = calendar;
    }

    @PostMapping("/api/resources/{resourceId}/bookings")
    public ResponseEntity<BookingResponse> book(@PathVariable UUID resourceId, @RequestBody BookingRequest request) {
        boolean success = calendar.tryBook(resourceId, new TimeSlot(request.start(), request.end()));
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new BookingResponse(success));
    }

    @GetMapping("/api/resources/{resourceId}/bookings")
    public List<TimeSlot> bookings(@PathVariable UUID resourceId) {
        return calendar.bookingsFor(resourceId);
    }

    @DeleteMapping("/api/resources/{resourceId}/bookings")
    public ResponseEntity<BookingResponse> cancel(@PathVariable UUID resourceId, @RequestBody BookingRequest request) {
        boolean removed = calendar.unbook(resourceId, new TimeSlot(request.start(), request.end()));
        HttpStatus status = removed ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(new BookingResponse(removed));
    }
}

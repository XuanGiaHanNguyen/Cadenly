package com.cadenly.scheduler.web;

import com.cadenly.scheduler.model.PlacedTaskResponse;
import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.port.BookingStore;
import com.cadenly.scheduler.port.TaskStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutations on an already-placed task. A placed task is really just a
 * (owner, slot) booking on the BookingStore plus a TaskStore entry for
 * dashboard display, so cancel/reschedule here means keeping both in sync
 * rather than owning any new state of its own. Requires an authenticated
 * session (see SecurityConfig); CORS is configured centrally there too.
 */
@RestController
public class TaskController {

    public record RescheduleRequest(Instant start, Instant end) {
    }

    private final TaskStore taskBoard;
    private final BookingStore calendar;

    public TaskController(TaskStore taskBoard, BookingStore calendar) {
        this.taskBoard = taskBoard;
        this.calendar = calendar;
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID taskId) {
        Optional<PlacedTaskResponse> existing = taskBoard.findPlaced(taskId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlacedTaskResponse task = existing.get();
        calendar.unbook(task.owner(), new TimeSlot(task.start(), task.end()));
        taskBoard.removePlaced(taskId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Frees the old slot then books the new one; if the new slot is taken,
     * the old slot is restored so a failed reschedule never leaves the task
     * unbooked.
     */
    @PatchMapping("/api/tasks/{taskId}")
    public ResponseEntity<PlacedTaskResponse> reschedule(@PathVariable UUID taskId, @RequestBody RescheduleRequest request) {
        Optional<PlacedTaskResponse> existing = taskBoard.findPlaced(taskId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlacedTaskResponse task = existing.get();
        TimeSlot oldSlot = new TimeSlot(task.start(), task.end());
        TimeSlot newSlot = new TimeSlot(request.start(), request.end());

        calendar.unbook(task.owner(), oldSlot);
        boolean booked = calendar.tryBook(task.owner(), newSlot);
        if (!booked) {
            calendar.tryBook(task.owner(), oldSlot);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(task);
        }

        PlacedTaskResponse updated = new PlacedTaskResponse(task.id(), task.description(), task.owner(), request.start(), request.end(),
                task.priority(), task.estimatedDurationMinutes());
        taskBoard.replacePlaced(updated);
        return ResponseEntity.ok(updated);
    }
}

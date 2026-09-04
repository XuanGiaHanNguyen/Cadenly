package com.cadenly.scheduler.web;

import com.cadenly.scheduler.model.OwnerSummary;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import com.cadenly.scheduler.port.OwnerDirectory;
import com.cadenly.scheduler.port.TaskStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read endpoints for the dashboard - everything else in this service is
 * write-only (submit tasks, book a slot) plus a live WebSocket broadcast,
 * with nothing to fetch current state from on page load. These exist
 * purely to give a freshly-opened dashboard something to render before
 * any live event arrives. All require an authenticated session (see
 * SecurityConfig) - CORS is configured centrally there too, not per
 * controller.
 */
@RestController
public class DashboardController {

    private final TaskStore taskBoard;
    private final OwnerDirectory ownerDirectory;

    public DashboardController(TaskStore taskBoard, OwnerDirectory ownerDirectory) {
        this.taskBoard = taskBoard;
        this.ownerDirectory = ownerDirectory;
    }

    @GetMapping("/api/tasks")
    public TaskSubmissionResponse tasks() {
        return taskBoard.all();
    }

    @GetMapping("/api/owners")
    public List<OwnerSummary> owners() {
        return ownerDirectory.all();
    }

    @GetMapping("/api/owners/{ownerId}/tasks")
    public TaskSubmissionResponse tasksForOwner(@PathVariable UUID ownerId) {
        return taskBoard.forOwner(ownerId);
    }
}

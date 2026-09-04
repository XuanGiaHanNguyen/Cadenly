package com.cadenly.scheduler.web;

import com.cadenly.scheduler.model.TaskSubmissionRequest;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import com.cadenly.scheduler.service.SchedulingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The REST adapter over SchedulingService.submitTasks - the Phase 1-2
 * design's "the recording pipeline calls a single scheduler-engine method"
 * seam. Server-to-server from the recording pipeline (Service B); the
 * dashboard also calls this directly for demo purposes.
 *
 * Deliberately left permitAll() in SecurityConfig (Phase 10 auth
 * migration) rather than requiring a session, so Service B's integration
 * contract doesn't change. TODO: harden this with a shared-secret header
 * (e.g. X-Service-Key) checked in a lightweight filter if this ever runs
 * somewhere less trusted than one local machine - deliberately deferred
 * for now, not forgotten.
 */
@RestController
public class TaskSubmissionController {

    private final SchedulingService schedulingService;

    public TaskSubmissionController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping("/api/tasks/submit")
    public TaskSubmissionResponse submit(@RequestBody TaskSubmissionRequest request) {
        return schedulingService.submitTasks(request.tasks());
    }
}

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
 * seam. Called server-to-server from the recording pipeline, not from a
 * browser, so no CORS configuration is needed here (unlike BookingController).
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

package com.cadenly.scheduler.model;

import java.util.List;

public record ScheduleResult(
        List<ScheduledTask> placed,
        List<Task> rejected,
        long totalWeightScheduled
) {
}

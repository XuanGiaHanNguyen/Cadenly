package com.cadenly.scheduler.model;

import java.util.List;

public record TaskSubmissionRequest(List<TaskSubmissionItem> tasks) {
}

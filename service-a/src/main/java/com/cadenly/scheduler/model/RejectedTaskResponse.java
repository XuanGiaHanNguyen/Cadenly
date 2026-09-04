package com.cadenly.scheduler.model;

import java.util.UUID;

public record RejectedTaskResponse(String description, UUID owner, String reason) {
}

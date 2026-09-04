package com.cadenly.scheduler.model;

/** ownerNameRaw is unchanged from what Service B sent - never reached the scheduler. */
public record UnresolvedTaskResponse(String ownerNameRaw, String description, String reason) {
}

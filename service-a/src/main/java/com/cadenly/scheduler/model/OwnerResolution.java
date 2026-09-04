package com.cadenly.scheduler.model;

import java.util.UUID;

public record OwnerResolution(UUID ownerId, String unresolvedReason) {
    public static OwnerResolution resolved(UUID id) {
        return new OwnerResolution(id, null);
    }

    public static OwnerResolution unassigned(String reason) {
        return new OwnerResolution(null, reason);
    }

    public boolean isResolved() {
        return ownerId != null;
    }
}

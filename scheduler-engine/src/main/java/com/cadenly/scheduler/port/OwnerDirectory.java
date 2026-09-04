package com.cadenly.scheduler.port;

import com.cadenly.scheduler.model.OwnerSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves owner names to accounts and lists known accounts. Account
 * creation itself happens through registration (real email+password
 * accounts, see AuthController) - not through this port, which is only
 * the scheduling-side "who does this name refer to" lookup.
 *
 * The production implementation (JpaOwnerDirectory) is backed by the
 * users + user_name_aliases tables; the fast test suite uses the original
 * in-memory UserDirectoryService, which now implements this port instead
 * of being a standalone class.
 */
public interface OwnerDirectory {

    /** Expects an already-normalized (trimmed, lowercased) name. */
    Optional<UUID> lookup(String normalizedName);

    List<OwnerSummary> all();
}

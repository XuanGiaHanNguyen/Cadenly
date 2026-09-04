package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.OwnerResolution;
import com.cadenly.scheduler.port.OwnerDirectory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * "Low-confidence" is defined as exactly two things: an empty (post-trim)
 * owner string, or an exact match (post-normalization) against
 * NO_OWNER_BLOCKLIST - not fuzzy/edit-distance matching against known
 * names. Most blocklist entries are literal strings observed from real
 * local-LLM extraction output in Phase 6 testing ("", "Unknown",
 * "someone", "no owner mentioned"), not speculative coverage. Any owner
 * string that fails both the blocklist check and the directory lookup is
 * reported as "not found in directory" (a distinct reason from "no owner
 * stated") - that distinction is what lets the blocklist be extended from
 * real evidence over time instead of guessing every possible phrasing
 * upfront.
 */
@Component
public class OwnerResolver {

    private static final Set<String> NO_OWNER_BLOCKLIST = Set.of(
            "unassigned", "unknown", "someone", "somebody", "anybody",
            "no owner mentioned", "not specified", "not mentioned",
            "n/a", "none", "tbd", "unclear"
    );

    private final OwnerDirectory userDirectory;

    public OwnerResolver(OwnerDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    public OwnerResolution resolve(String rawOwner) {
        String normalized = rawOwner == null ? "" : rawOwner.strip().toLowerCase();

        if (normalized.isEmpty() || NO_OWNER_BLOCKLIST.contains(normalized)) {
            return OwnerResolution.unassigned("no owner stated");
        }

        return userDirectory.lookup(normalized)
                .map(OwnerResolution::resolved)
                .orElseGet(() -> OwnerResolution.unassigned("owner name '" + rawOwner + "' not found in directory"));
    }
}

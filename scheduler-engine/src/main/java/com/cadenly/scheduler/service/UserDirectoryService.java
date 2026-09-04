package com.cadenly.scheduler.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal hardcoded demo directory. Sarah has two name variants mapping to
 * the same UUID, proving consistent matching across differently-specific
 * mentions of the same person - a real integration would replace this with
 * an actual user store.
 */
@Service
public class UserDirectoryService {

    public static final UUID SARAH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID JOHN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID PRIYA_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Map<String, UUID> NAME_VARIANTS = Map.of(
            "sarah kim", SARAH_ID,
            "sarah", SARAH_ID,
            "john", JOHN_ID,
            "priya", PRIYA_ID
    );

    /**
     * Load-test-only pattern: "loadtest-user-<n>" resolves to a UUID derived
     * deterministically via UUID.nameUUIDFromBytes rather than a static map
     * entry - lets Phase 8's load test generate as many genuinely distinct
     * owners as needed without hardcoding dozens of UUIDs, while still
     * exercising this real lookup path (not a bypass of it).
     */
    private static final Pattern LOAD_TEST_USER_PATTERN = Pattern.compile("loadtest-user-\\d+");

    /** Expects an already-normalized (trimmed, lowercased) name. */
    public Optional<UUID> lookup(String normalizedName) {
        UUID mapped = NAME_VARIANTS.get(normalizedName);
        if (mapped != null) {
            return Optional.of(mapped);
        }
        if (LOAD_TEST_USER_PATTERN.matcher(normalizedName).matches()) {
            return Optional.of(UUID.nameUUIDFromBytes(normalizedName.getBytes(StandardCharsets.UTF_8)));
        }
        return Optional.empty();
    }
}

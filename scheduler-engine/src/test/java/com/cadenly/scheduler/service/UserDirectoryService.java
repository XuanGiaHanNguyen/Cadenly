package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.OwnerSummary;
import com.cadenly.scheduler.port.OwnerDirectory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fast-test fixture: the original hardcoded demo directory, kept exactly as
 * it behaved before the Phase 10 Postgres migration. Sarah has two name
 * variants mapping to the same UUID, proving consistent matching across
 * differently-specific mentions of the same person.
 *
 * Production uses JpaOwnerDirectory (backed by users + user_name_aliases)
 * instead - this class now only exists to let SchedulingServiceTest and
 * OwnerResolverTest run against an OwnerDirectory with zero Spring context
 * and zero database, exactly as fast as before the migration.
 */
public class UserDirectoryService implements OwnerDirectory {

    public static final UUID SARAH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID JOHN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID PRIYA_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Map<String, UUID> NAME_VARIANTS = Map.of(
            "sarah kim", SARAH_ID,
            "sarah", SARAH_ID,
            "john", JOHN_ID,
            "priya", PRIYA_ID
    );

    private static final Map<UUID, String> DISPLAY_NAMES = Map.of(
            SARAH_ID, "Sarah Kim",
            JOHN_ID, "John",
            PRIYA_ID, "Priya"
    );

    /**
     * Load-test-only pattern: "loadtest-user-<n>" resolves to a UUID derived
     * deterministically via UUID.nameUUIDFromBytes rather than a static map
     * entry - lets the load test generate as many genuinely distinct owners
     * as needed without hardcoding dozens of UUIDs, while still exercising
     * this real lookup path (not a bypass of it).
     */
    private static final Pattern LOAD_TEST_USER_PATTERN = Pattern.compile("loadtest-user-\\d+");

    @Override
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

    @Override
    public List<OwnerSummary> all() {
        return DISPLAY_NAMES.entrySet().stream()
                .map(entry -> new OwnerSummary(entry.getKey(), entry.getValue()))
                .toList();
    }
}

package com.cadenly.scheduler.persistence;

import com.cadenly.scheduler.model.OwnerSummary;
import com.cadenly.scheduler.port.OwnerDirectory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Production OwnerDirectory: users + user_name_aliases. See OwnerDirectory's
 * class doc for why account creation itself isn't part of this port.
 */
@Component
public class JpaOwnerDirectory implements OwnerDirectory {

    /**
     * Load-test-only pattern, same one UserDirectoryService (the fast-test
     * fixture) has always had: lets the load test generate as many
     * genuinely distinct owners as needed without registering real
     * accounts for each. Unlike the in-memory fixture, this lookup must
     * satisfy tasks.owner_id's foreign key, so a first-sight match here
     * also provisions a minimal, unusable-password placeholder row -
     * still gated by the exact same regex, so it's not reachable from any
     * real owner name.
     */
    private static final Pattern LOAD_TEST_USER_PATTERN = Pattern.compile("loadtest-user-\\d+");

    private final UserJpaRepository userJpaRepository;
    private final UserNameAliasJpaRepository aliasJpaRepository;
    private final PasswordEncoder passwordEncoder;

    public JpaOwnerDirectory(UserJpaRepository userJpaRepository, UserNameAliasJpaRepository aliasJpaRepository,
                              PasswordEncoder passwordEncoder) {
        this.userJpaRepository = userJpaRepository;
        this.aliasJpaRepository = aliasJpaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<UUID> lookup(String normalizedName) {
        Optional<UUID> aliased = aliasJpaRepository.findByAlias(normalizedName).map(UserNameAliasEntity::getUserId);
        if (aliased.isPresent()) {
            return aliased;
        }
        if (LOAD_TEST_USER_PATTERN.matcher(normalizedName).matches()) {
            return Optional.of(provisionLoadTestUser(normalizedName));
        }
        return Optional.empty();
    }

    /**
     * Deliberately not wrapped in one @Transactional method: each
     * repository call below commits (or fails) independently, so a
     * constraint violation caught from one doesn't leave a poisoned
     * persistence context for the next call in the same request - the
     * concurrent-load equivalent of BookingStore.tryBook's "lost the race,
     * report it, keep going" contract. Many concurrent requests can hit
     * this for the SAME never-before-seen name at once (e.g. 200 identical
     * "loadtest-user-999" submissions); since the id is deterministic,
     * whichever request's INSERT actually wins doesn't matter - every
     * caller ends up with the same id either way.
     */
    private UUID provisionLoadTestUser(String normalizedName) {
        UUID id = UUID.nameUUIDFromBytes(normalizedName.getBytes(StandardCharsets.UTF_8));
        if (!userJpaRepository.existsById(id)) {
            try {
                userJpaRepository.save(new UserEntity(id, normalizedName + "@loadtest.local", normalizedName,
                        passwordEncoder.encode(UUID.randomUUID().toString())));
            } catch (DataIntegrityViolationException e) {
                // lost the race to provision this deterministic id to another concurrent lookup - fine, same id either way.
            }
        }
        if (aliasJpaRepository.findByAlias(normalizedName).isEmpty()) {
            try {
                aliasJpaRepository.save(new UserNameAliasEntity(id, normalizedName));
            } catch (DataIntegrityViolationException e) {
                // same race, on the alias row instead.
            }
        }
        return id;
    }

    @Override
    public List<OwnerSummary> all() {
        return userJpaRepository.findAll().stream()
                .map(u -> new OwnerSummary(u.getId(), u.getDisplayName()))
                .toList();
    }
}

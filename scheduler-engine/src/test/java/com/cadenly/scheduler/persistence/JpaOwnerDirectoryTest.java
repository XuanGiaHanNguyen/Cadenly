package com.cadenly.scheduler.persistence;

import com.cadenly.scheduler.port.OwnerDirectory;
import com.cadenly.scheduler.testsupport.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract proof that JpaOwnerDirectory (production) resolves names the
 * same way UserDirectoryService (the fast-test fake) always has - same
 * scenarios SchedulingServiceTest already covers against the fake, run
 * here against the real Postgres-backed implementation.
 */
@Tag("integration")
class JpaOwnerDirectoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OwnerDirectory ownerDirectory;

    @Test
    void seededDemoAliases_resolveToTheSameAccount() {
        Optional<UUID> short_ = ownerDirectory.lookup("sarah");
        Optional<UUID> full = ownerDirectory.lookup("sarah kim");

        assertThat(short_).isPresent();
        assertThat(full).isPresent();
        assertThat(short_).isEqualTo(full);
    }

    @Test
    void unknownName_isEmpty() {
        assertThat(ownerDirectory.lookup("definitely not a real alias")).isEmpty();
    }

    @Test
    void loadTestUserPattern_isProvisionedDeterministically() {
        Optional<UUID> first = ownerDirectory.lookup("loadtest-user-42");
        Optional<UUID> second = ownerDirectory.lookup("loadtest-user-42");

        assertThat(first).isPresent();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void seededDemoOwners_appearInAll() {
        assertThat(ownerDirectory.all()).extracting("name").contains("Sarah Kim", "John", "Priya");
    }
}

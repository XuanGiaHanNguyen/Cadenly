package com.cadenly.scheduler.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for the "integration" test tier: a real Postgres, migrated by the
 * same Flyway scripts production uses - see Phase 10 design notes for why
 * this replaces H2/an embedded substitute (neither supports the GiST
 * EXCLUDE constraints this schema depends on).
 *
 * Defaults to a throwaway Testcontainers instance (requires Docker). When
 * Docker isn't available - a disk-constrained sandbox, a CI runner without
 * Docker-in-Docker - point these tests at any already-running Postgres
 * instead by setting -Dcadenly.it.datasource.url (and optionally
 * .username/.password, default "cadenly"/"cadenly"). Same test classes,
 * same assertions either way; only the source of the database changes.
 *
 * Singleton-container pattern for the Testcontainers path: one container,
 * started once and never explicitly stopped (Testcontainers' Ryuk reaper
 * cleans it up when the JVM exits), shared by every subclass in the same
 * test run via the static field - deliberately not using
 * @Testcontainers/@Container's automatic per-class lifecycle, which would
 * try to stop this container after the first subclass's tests finish and
 * break every subclass after it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresIntegrationTest {

    private static final String EXTERNAL_DB_URL = System.getProperty("cadenly.it.datasource.url");

    static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_DB_URL != null
            ? null
            : new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_DB_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB_URL);
            registry.add("spring.datasource.username", () -> System.getProperty("cadenly.it.datasource.username", "cadenly"));
            registry.add("spring.datasource.password", () -> System.getProperty("cadenly.it.datasource.password", "cadenly"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }
}

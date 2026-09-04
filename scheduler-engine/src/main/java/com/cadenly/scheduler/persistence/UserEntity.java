package com.cadenly.scheduler.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Id is application-assigned (java.util.UUID.randomUUID(), or a
 * deterministic derived UUID for load-test provisioning - see
 * JpaOwnerDirectory) rather than Hibernate-generated: @GeneratedValue-style
 * generators always overwrite any pre-set id at insert time, which would
 * break the deterministic-id requirement that load-test provisioning
 * depends on. One assignment strategy across all entities avoids that trap
 * entirely - see TaskEntity for the same reasoning.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Both null until onboarding (see AuthController.completeOnboarding); occupation != null means onboarding is done. */
    private String occupation;

    @Column(name = "calendar_preference")
    private String calendarPreference;

    protected UserEntity() {
    }

    public UserEntity(String email, String displayName, String passwordHash) {
        this(UUID.randomUUID(), email, displayName, passwordHash);
    }

    /** For the one caller that needs a deterministic id - see JpaOwnerDirectory's load-test provisioning. */
    public UserEntity(UUID id, String email, String displayName, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getCalendarPreference() {
        return calendarPreference;
    }

    public void setCalendarPreference(String calendarPreference) {
        this.calendarPreference = calendarPreference;
    }
}

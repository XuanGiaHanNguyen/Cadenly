package com.cadenly.scheduler.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_name_aliases")
public class UserNameAliasEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String alias;

    protected UserNameAliasEntity() {
    }

    public UserNameAliasEntity(UUID userId, String alias) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.alias = alias;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAlias() {
        return alias;
    }
}

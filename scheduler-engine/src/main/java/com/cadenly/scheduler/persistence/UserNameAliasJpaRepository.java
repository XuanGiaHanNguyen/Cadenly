package com.cadenly.scheduler.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserNameAliasJpaRepository extends JpaRepository<UserNameAliasEntity, UUID> {
    Optional<UserNameAliasEntity> findByAlias(String alias);
}

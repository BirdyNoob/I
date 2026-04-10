package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.identity.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    List<UserGroup> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    Optional<UserGroup> findByIdAndTenantId(UUID id, UUID tenantId);
}

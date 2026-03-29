package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.identity.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    /** All tenant memberships for a given user. */
    List<TenantUser> findByUserId(UUID userId);

    /** Specific membership for a user in a specific tenant. */
    Optional<TenantUser> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    /** All members of a specific tenant. */
    List<TenantUser> findByTenantId(UUID tenantId);
}

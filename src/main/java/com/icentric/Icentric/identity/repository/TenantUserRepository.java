package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.identity.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    /** All tenant memberships for a given user. */
    List<TenantUser> findByUserId(UUID userId);

    /** Specific membership for a user in a specific tenant. */
    Optional<TenantUser> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);

    /** All members of a specific tenant. */
    List<TenantUser> findByTenantId(UUID tenantId);

    List<TenantUser> findByTenantIdAndUserIdIn(UUID tenantId, Collection<UUID> userIds);

    long countByTenantIdAndUserIdIn(UUID tenantId, Collection<UUID> userIds);

    @Query("select tu.userId from TenantUser tu where tu.tenantId = :tenantId and tu.userId in :userIds")
    List<UUID> findUserIdsByTenantIdAndUserIdIn(UUID tenantId, Collection<UUID> userIds);
}

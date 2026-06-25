package com.icentric.Icentric.audit.repository;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findAll(Pageable pageable);

    Page<AuditLog> findByTenantSlug(String tenantSlug, Pageable pageable);

    List<AuditLog> findByUserId(UUID userId);

    List<AuditLog> findByAction(AuditAction action);

    Page<AuditLog> findByTenantSlugAndActionIn(String tenantSlug, List<AuditAction> actions, Pageable pageable);

    Page<AuditLog> findByTenantSlugAndActionInAndUserIdIn(String tenantSlug, List<AuditAction> actions, List<UUID> userIds, Pageable pageable);

    List<AuditLog> findByTenantSlugAndActionAndCreatedAtBetween(
            String tenantSlug,
            AuditAction action,
            Instant from,
            Instant to
    );

    @Query("""
            SELECT COUNT(DISTINCT a.tenantSlug)
            FROM AuditLog a
            WHERE a.createdAt >= :since
              AND a.tenantSlug IS NOT NULL
              AND a.tenantSlug <> ''
            """)
    long countActiveTenantsSince(Instant since);

    @Query("""
            SELECT COUNT(DISTINCT a.tenantSlug)
            FROM AuditLog a
            WHERE a.createdAt >= :from
              AND a.createdAt < :to
              AND a.tenantSlug IS NOT NULL
              AND a.tenantSlug <> ''
            """)
    long countActiveTenantsBetween(Instant from, Instant to);

    @Query("""
            SELECT COUNT(DISTINCT a.userId)
            FROM AuditLog a
            WHERE a.createdAt >= :since
              AND a.userId IS NOT NULL
            """)
    long countDistinctActiveUsersSince(Instant since);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}

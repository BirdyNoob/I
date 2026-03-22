package com.icentric.Icentric.audit.repository;

import com.icentric.Icentric.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAll(Pageable pageable);

    Page<AuditLog> findByTenantSlug(String tenantSlug, Pageable pageable);

    List<AuditLog> findByUserId(UUID userId);
}

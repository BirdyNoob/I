package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.audit.entity.AuditLog;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        String tenant = TenantContext.getTenant();
        if ("system".equals(tenant)) {
            // Platform Admins see all logs
            return repository.findAll(pageable);
        } else {
            // Tenant Admins see only their tenant's logs
            return repository.findByTenantSlug(tenant, pageable);
        }
    }

    public void log(
            UUID userId,
            String action,
            String entityType,
            String entityId,
            String details
    ) {
        String tenant = TenantContext.getTenant();

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setUserId(userId);
        log.setTenantSlug(tenant);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setCreatedAt(Instant.now());

        repository.save(log);
    }
}

package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.entity.AuditLog;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public Page<AuditLog> getLogs(
            Pageable pageable,
            AuditAction action,
            String entityType,
            UUID userId,
            Instant createdFrom,
            Instant createdTo
    ) {
        String tenant = TenantContext.getTenant();
        Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!"system".equals(tenant)) {
                predicates.add(criteriaBuilder.equal(root.get("tenantSlug"), tenant));
            }
            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), entityType));
            }
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        return repository.findAll(spec, pageable);
    }

    public void log(
            UUID userId,
            AuditAction action,
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

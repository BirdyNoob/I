package com.icentric.Icentric.tenant;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets the Postgres search_path for the current JPA connection.
 *
 * MUST be called at the start of any @Transactional method that touches
 * tenant-schema tables (simulation_attempts, simulation_rule_violations, etc.).
 *
 * Using Propagation.MANDATORY so it always joins the caller's transaction
 * (prevents accidental use outside a transaction context).
 */
@Slf4j
@Service
public class TenantSchemaService {

    private final EntityManager entityManager;

    public TenantSchemaService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Reads the current tenant from TenantContext and sets the search_path
     * for the active JPA transaction. Caller MUST be @Transactional.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyCurrentTenantSearchPath() {
        String tenant = TenantContext.getTenant();

        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("Missing tenant in request context");
        }

        String schema = resolveSchema(tenant);
        log.debug("Setting search_path to: {}", schema);
        entityManager.createNativeQuery("SET search_path TO " + schema)
                .executeUpdate();
    }

    public String resolveSchema(String tenant) {
        if ("system".equals(tenant)) {
            return "system";
        }
        if (tenant.startsWith("tenant_")) {
            String suffix = tenant.substring("tenant_".length());
            if (!suffix.matches("[a-zA-Z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid tenant schema: " + tenant);
            }
            return "\"" + tenant + "\"";
        }
        if (!tenant.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid tenant slug: " + tenant);
        }
        return "\"tenant_" + tenant + "\"";
    }
}

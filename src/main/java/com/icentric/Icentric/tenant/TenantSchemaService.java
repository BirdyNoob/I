package com.icentric.Icentric.tenant;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class TenantSchemaService {

    private final EntityManager entityManager;

    public TenantSchemaService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void applyCurrentTenantSearchPath() {
        String tenant = TenantContext.getTenant();

        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("Missing tenant in request context");
        }

        String schema;
        if ("system".equals(tenant)) {
            schema = "system";
        } else {
            if (!tenant.matches("[a-zA-Z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid tenant slug: " + tenant);
            }
            schema = "tenant_" + tenant;
        }

        entityManager.createNativeQuery("SET search_path TO " + schema)
                .executeUpdate();
    }
}

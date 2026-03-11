package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final TenantUserBootstrapService bootstrapService;

    public TenantService(
            TenantRepository tenantRepository,
            TenantProvisioningService provisioningService,
            TenantUserBootstrapService bootstrapService) {
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.bootstrapService = bootstrapService;
    }

    public Tenant createTenant(String slug, String companyName, String adminEmail) {

        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new IllegalStateException("Tenant slug already exists: " + slug);
        }

        Tenant tenant = new Tenant(slug, companyName);

        tenantRepository.save(tenant);

        provisioningService.provisionTenantSchema(slug);

        bootstrapService.createSuperAdmin(slug, adminEmail);

        return tenant;
    }
}

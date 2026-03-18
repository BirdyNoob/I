package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationStartupRunner.class);

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public TenantMigrationStartupRunner(
            TenantRepository tenantRepository,
            TenantProvisioningService tenantProvisioningService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) {
            log.info("No tenant schemas to migrate at startup.");
            return;
        }

        for (var tenant : tenants) {
            log.info("Migrating tenant schema for slug={}", tenant.getSlug());
            tenantProvisioningService.provisionTenantSchema(tenant.getSlug());
        }
    }
}

package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.platform.dto.TenantResponse;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final TenantUserBootstrapService bootstrapService;
    private final AuditService auditService;


    public TenantService(
            TenantRepository tenantRepository,
            TenantProvisioningService provisioningService,
            TenantUserBootstrapService bootstrapService,
            AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.bootstrapService = bootstrapService;
        this.auditService = auditService;
    }

    public Tenant createTenant(String slug, String companyName, String adminEmail, String adminPassword) {

        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new IllegalStateException("Tenant slug already exists: " + slug);
        }

        Tenant tenant = new Tenant(slug, companyName);

        tenantRepository.save(tenant);

        provisioningService.provisionTenantSchema(slug);

        bootstrapService.createSuperAdmin(slug, adminEmail, adminPassword);

        UUID actorId = currentActorUserId();
        if (actorId != null) {
            auditService.logForTenant(
                    actorId,
                    AuditAction.TENANT_CREATED,
                    "TENANT",
                    tenant.getId().toString(),
                    "Platform admin " + actorId + " created tenant " + tenant.getCompanyName()
                            + " [" + tenant.getSlug() + "] with bootstrap admin " + adminEmail,
                    "system"
            );
        }

        return tenant;
    }
    public List<TenantResponse> getAllTenants() {

        return tenantRepository.findAll()
                .stream()
                .map(t -> new TenantResponse(
                        t.getId(),
                        t.getCompanyName(),
                        t.getSlug(),
                        t.getCreatedAt()
                ))
                .toList();
    }

    private UUID currentActorUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        return userIdRaw == null ? null : UUID.fromString(userIdRaw.toString());
    }
}

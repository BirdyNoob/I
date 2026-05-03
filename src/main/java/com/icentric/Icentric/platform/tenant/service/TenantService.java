package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.platform.dto.TenantResponse;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.icentric.Icentric.common.mail.EmailService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final TenantUserBootstrapService bootstrapService;
    private final AuditService auditService;
    private final EmailService emailService;

    public TenantService(
            TenantRepository tenantRepository,
            TenantProvisioningService provisioningService,
            TenantUserBootstrapService bootstrapService,
            AuditService auditService,
            EmailService emailService) {
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.bootstrapService = bootstrapService;
        this.auditService = auditService;
        this.emailService = emailService;
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

        // Send onboarding email with raw credentials
        String platformUrl = "http://localhost:3000/login?tenant=" + slug; // Defaults to localhost, can be configured via app properties later
        Map<String, Object> emailVars = Map.of(
                "tenantName", companyName,
                "portalUrl", slug + ".icentric.com",
                "adminEmail", adminEmail,
                "adminPassword", adminPassword,
                "setupUrl", platformUrl,
                "loginUrl", platformUrl,
                "planName", "Enterprise",
                "seatLimit", 500
        );
        emailService.sendTemplateEmail(
                adminEmail,
                "Welcome to AISafe — Your company is ready",
                "AISafe_Email_TenantAdmin_Welcome",
                emailVars
        );

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

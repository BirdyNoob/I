package com.icentric.Icentric.platform.impersonation.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.platform.impersonation.entity.ImpersonationSession;
import com.icentric.Icentric.platform.impersonation.repository.ImpersonationSessionRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.security.JwtService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ImpersonationService {

    private final ImpersonationSessionRepository repository;
    private final JwtService jwtService;
    private final PlatformAdminRepository adminRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final AuditService auditService;

    public ImpersonationService(
            ImpersonationSessionRepository repository,
            JwtService jwtService,
            PlatformAdminRepository adminRepository,
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.adminRepository = adminRepository;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.auditService = auditService;
    }

    /**
     * Starts an impersonation session.
     *
     * @param adminEmail   email of the platform admin (extracted from JWT
     *                     principal)
     * @param targetUserId UUID of the tenant user to impersonate
     * @param tenantSlug   the target tenant's slug
     * @param reason       justification text
     * @return signed impersonation JWT
     */
    public String startSession(
            String adminEmail, // Fix #6: was (incorrectly) UUID before
            UUID targetUserId,
            String tenantSlug,
            String reason) {
        var platformAdmin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Platform admin not found: " + adminEmail));
        UUID platformAdminId = platformAdmin.getId();
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantSlug));
        TenantUser targetMembership = tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Target user not found in tenant: " + tenantSlug + " (userId=" + targetUserId + ")"
                ));
        String role = toAuthority(targetMembership.getRole());

        ImpersonationSession session = new ImpersonationSession();
        session.setPlatformAdminId(platformAdminId);
        session.setImpersonatedUserId(targetUserId);
        session.setTenantSlug(tenantSlug);
        session.setReason(reason);

        repository.save(session);

        String adminLabel = (platformAdmin.getFullName() != null && !platformAdmin.getFullName().isBlank()
                ? platformAdmin.getFullName()
                : platformAdmin.getEmail()) + " <" + platformAdmin.getEmail() + ">";
        auditService.logForTenant(
                platformAdminId,
                AuditAction.IMPERSONATION_STARTED,
                "IMPERSONATION_SESSION",
                session.getId().toString(),
                adminLabel + " started impersonation for user " + targetUserId
                        + " in tenant " + tenantSlug
                        + " with role " + role
                        + ". Reason: " + reason,
                "system"
        );

        return jwtService.generateImpersonationToken(
                adminEmail,
                targetUserId,
                role,
                tenantSlug,
                platformAdminId,
                session.getId());
    }

    private String toAuthority(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalStateException("Target user has no role assigned");
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}

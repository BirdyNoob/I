package com.icentric.Icentric.platform.impersonation.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.platform.impersonation.entity.ImpersonationSession;
import com.icentric.Icentric.platform.impersonation.repository.ImpersonationSessionRepository;
import com.icentric.Icentric.security.JwtService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImpersonationService {

    private final ImpersonationSessionRepository repository;
    private final JwtService jwtService;
    private final PlatformAdminRepository adminRepository;
    private final AuditService auditService;

    public ImpersonationService(
            ImpersonationSessionRepository repository,
            JwtService jwtService,
            PlatformAdminRepository adminRepository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.adminRepository = adminRepository;
        this.auditService = auditService;
    }

    /**
     * Starts an impersonation session.
     *
     * @param adminEmail   email of the platform admin (extracted from JWT
     *                     principal)
     * @param targetUserId UUID of the tenant user to impersonate
     * @param tenantSlug   the target tenant's slug
     * @param role         role to assign in the impersonation token
     * @param reason       justification text
     * @return signed impersonation JWT
     */
    public String startSession(
            String adminEmail, // Fix #6: was (incorrectly) UUID before
            UUID targetUserId,
            String tenantSlug,
            String role,
            String reason) {
        // Fix #6: resolve the actual UUID from the email stored in the JWT subject
        UUID platformAdminId = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Platform admin not found: " + adminEmail))
                .getId();

        ImpersonationSession session = new ImpersonationSession();
        session.setPlatformAdminId(platformAdminId);
        session.setImpersonatedUserId(targetUserId);
        session.setTenantSlug(tenantSlug);
        session.setReason(reason);

        repository.save(session);

        String adminLabel = adminRepository.findById(platformAdminId)
                .map(admin -> (admin.getFullName() != null && !admin.getFullName().isBlank() ? admin.getFullName() : admin.getEmail())
                        + " <" + admin.getEmail() + ">")
                .orElse(adminEmail);
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
}

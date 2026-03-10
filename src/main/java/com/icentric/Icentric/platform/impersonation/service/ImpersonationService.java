package com.icentric.Icentric.platform.impersonation.service;
import com.icentric.Icentric.platform.impersonation.entity.ImpersonationSession;
import com.icentric.Icentric.platform.impersonation.repository.ImpersonationSessionRepository;
import com.icentric.Icentric.security.JwtService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImpersonationService {

    private final ImpersonationSessionRepository repository;
    private final JwtService jwtService;

    public ImpersonationService(
            ImpersonationSessionRepository repository,
            JwtService jwtService
    ) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    public String startSession(
            UUID platformAdminId,
            UUID targetUserId,
            String tenantSlug,
            String role,
            String email,
            String reason
    ) {

        ImpersonationSession session = new ImpersonationSession();

        session.setPlatformAdminId(platformAdminId);
        session.setImpersonatedUserId(targetUserId);
        session.setTenantSlug(tenantSlug);
        session.setReason(reason);

        repository.save(session);

        return jwtService.generateImpersonationToken(
                email,
                targetUserId,
                role,
                tenantSlug,
                platformAdminId,
                session.getId()
        );
    }
}

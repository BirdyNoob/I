package com.icentric.Icentric.platform.tenant.controller;
import com.icentric.Icentric.platform.impersonation.dto.ImpersonationRequest;
import com.icentric.Icentric.platform.impersonation.service.ImpersonationService;
import com.icentric.Icentric.platform.tenant.dto.CreateTenantRequest;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.service.TenantService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {

    private final TenantService tenantService;
    private final ImpersonationService service;

    public PlatformTenantController(TenantService tenantService, ImpersonationService service) {
        this.tenantService = tenantService;
        this.service = service;
    }

    @PostMapping
    public Tenant createTenant(@RequestBody CreateTenantRequest request) {

        return tenantService.createTenant(
                request.slug(),
                request.companyName(),
                request.adminEmail()
        );
    }
    @PostMapping("/{slug}/impersonate")
    public String impersonate(
            @PathVariable String slug,
            @RequestBody ImpersonationRequest request
    ) {

        UUID platformAdminId =
                (UUID) SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();

        return service.startSession(
                platformAdminId,
                request.targetUserId(),
                slug,
                "ROLE_ADMIN",
                "target@tenant.com",
                request.reason()
        );
    }
}

package com.icentric.Icentric.platform.tenant.controller;

import com.icentric.Icentric.platform.impersonation.dto.ImpersonationRequest;
import com.icentric.Icentric.platform.impersonation.service.ImpersonationService;
import com.icentric.Icentric.platform.tenant.dto.CreateTenantRequest;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Fix #6: The principal in SecurityContext is the email string (set by
 * JwtAuthenticationFilter).
 * Previously the code tried to cast it to UUID, which would always fail.
 * Impersonation now looks up the admin by email via the repository/service.
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {

    private final TenantService tenantService;
    private final ImpersonationService impersonationService;

    public PlatformTenantController(TenantService tenantService, ImpersonationService impersonationService) {
        this.tenantService = tenantService;
        this.impersonationService = impersonationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public Tenant createTenant(@Valid @RequestBody CreateTenantRequest request) { // Fix #5: @Valid
        return tenantService.createTenant(
                request.slug(),
                request.companyName(),
                request.adminEmail());
    }

    @PostMapping("/{slug}/impersonate")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public String impersonate(
            @PathVariable String slug,
            @Valid @RequestBody ImpersonationRequest request, // Fix #5: @Valid
            @AuthenticationPrincipal String adminEmail // Fix #6: principal is email
    ) {
        return impersonationService.startSession(
                adminEmail, // pass email; service resolves UUID internally
                request.targetUserId(),
                slug,
                "ROLE_ADMIN",
                request.reason());
    }
    @GetMapping
    public String anything() {
        return "Anything is accessible without authentication!";
    }
}

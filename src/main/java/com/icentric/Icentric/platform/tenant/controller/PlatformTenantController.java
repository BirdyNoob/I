package com.icentric.Icentric.platform.tenant.controller;

import com.icentric.Icentric.platform.impersonation.dto.ImpersonationRequest;
import com.icentric.Icentric.platform.impersonation.service.ImpersonationService;
import com.icentric.Icentric.platform.tenant.dto.CreateTenantRequest;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Tenants Management (Platform)", description = "APIs for platform admins to create and impersonate tenants")
public class PlatformTenantController {

    private final TenantService tenantService;
    private final ImpersonationService impersonationService;

    public PlatformTenantController(TenantService tenantService, ImpersonationService impersonationService) {
        this.tenantService = tenantService;
        this.impersonationService = impersonationService;
    }

    @Operation(summary = "Create a new tenant", description = "Registers a new tenant/organization on the platform.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully created the tenant"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public Tenant createTenant(@Valid @RequestBody CreateTenantRequest request) { // Fix #5: @Valid
        return tenantService.createTenant(
                request.slug(),
                request.companyName(),
                request.adminEmail());
    }

    @Operation(summary = "Impersonate a tenant admin", description = "Generates a session token that allows a platform admin to log in as a specific user within a tenant's environment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully started impersonation session"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required"),
            @ApiResponse(responseCode = "404", description = "Tenant or target user not found")
    })
    @PostMapping("/{slug}/impersonate")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public String impersonate(
            @Parameter(description = "Slug of the tenant to log into") @PathVariable String slug,
            @Valid @RequestBody ImpersonationRequest request, // Fix #5: @Valid
            @Parameter(hidden = true) @AuthenticationPrincipal String adminEmail // Fix #6: principal is email
    ) {
        return impersonationService.startSession(
                adminEmail, // pass email; service resolves UUID internally
                request.targetUserId(),
                slug,
                "ROLE_ADMIN",
                request.reason());
    }
}

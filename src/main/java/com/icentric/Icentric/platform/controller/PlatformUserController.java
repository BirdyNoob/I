package com.icentric.Icentric.platform.controller;

import com.icentric.Icentric.platform.dto.TenantResponse;
import com.icentric.Icentric.platform.service.PlatformUserService;
import com.icentric.Icentric.platform.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@Tag(name = "Platform Tenants & Users", description = "APIs for platform admins to view tenants and their users")
public class PlatformUserController {

    private final PlatformUserService service;
    private final TenantService tenantService;

    public PlatformUserController(PlatformUserService service, TenantService tenantService) {
        this.service = service;
        this.tenantService = tenantService;
    }

    @Operation(summary = "Get users for a tenant", description = "Retrieves a list of all users within a specific tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved users"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/{tenantId}/users")
    public List<Map<String, Object>> getUsers(
            @Parameter(description = "UUID of the tenant") @PathVariable UUID tenantId
    ) {
        return service.getTenantUsers(tenantId);
    }

    @Operation(summary = "Get all tenants", description = "Retrieves a list of all tenants registered on the platform.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of tenants"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @GetMapping
    public List<TenantResponse> getTenants() {
        return tenantService.getAllTenants();
    }
}

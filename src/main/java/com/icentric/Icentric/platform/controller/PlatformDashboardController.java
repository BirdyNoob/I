package com.icentric.Icentric.platform.controller;

import com.icentric.Icentric.platform.dto.PlatformDashboardResponse;
import com.icentric.Icentric.platform.dto.CrossTenantAnalyticsResponse;
import com.icentric.Icentric.platform.service.PlatformDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/dashboard")
@RequiredArgsConstructor
@Tag(name = "Platform Dashboard", description = "Platform admin dashboard metrics")
public class PlatformDashboardController {

    private final PlatformDashboardService platformDashboardService;

    @Operation(summary = "Get platform admin dashboard", description = "Returns platform-wide KPIs, tenant summaries, recent activity, and content health.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public PlatformDashboardResponse getDashboard() {
        return platformDashboardService.getDashboard();
    }

    @Operation(summary = "Get cross-tenant analytics", description = "Returns cross-tenant KPIs, trends, tenant comparison, risks, and content impact.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved cross-tenant analytics"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @GetMapping("/cross-tenant-analytics")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public CrossTenantAnalyticsResponse getCrossTenantAnalytics() {
        return platformDashboardService.getCrossTenantAnalytics();
    }
}

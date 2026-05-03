package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.TenantAdminDetailsResponse;
import com.icentric.Icentric.learning.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/profile")
@Tag(name = "Admin Profile", description = "APIs for logged-in tenant admins to view their profile and tenant details")
public class TenantAdminProfileController {

    private final AdminAnalyticsService service;

    public TenantAdminProfileController(AdminAnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Get admin and tenant details", description = "Retrieves details of the logged-in admin and their associated tenant (plan, seats, etc.)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved details"),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin role required")
    })
    @GetMapping("/details")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TenantAdminDetailsResponse getDetails() {
        return service.getTenantAdminDetails();
    }
}

package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantAdminDetailsResponse(
        UUID tenantId,
        String companyName,
        String slug,
        String plan,
        Integer maxSeats,
        long activeSeats,
        String status,
        Instant tenantCreatedAt,
        Instant trialEndsAt,
        String adminName,
        String adminEmail,
        String adminRole,
        String adminDepartment,
        String adminLocation,
        Instant adminLastLoginAt
) {}

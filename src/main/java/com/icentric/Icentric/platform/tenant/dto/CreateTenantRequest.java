package com.icentric.Icentric.platform.tenant.dto;

public record CreateTenantRequest(
        String slug,
        String companyName,
        String adminEmail
) {}

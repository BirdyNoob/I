package com.icentric.Icentric.platform.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TenantResponse(
        Summary summary,
        List<TenantItem> tenants,
        Pagination pagination
) {
    public record Summary(
            long totalTenants,
            long activeTenants,
            long trialTenants
    ) {}

    public record TenantItem(
            UUID tenantId,
            String name,
            String slug,
            String logo,
            String plan,
            long userCount,
            Integer seatLimit,
            int completionPercentage,
            LocalDate createdAt,
            String status
    ) {}

    public record Pagination(
            long totalItems,
            int itemsPerPage,
            int currentPage
    ) {}
}

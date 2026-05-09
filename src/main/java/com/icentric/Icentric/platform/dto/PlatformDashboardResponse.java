package com.icentric.Icentric.platform.dto;

import java.util.List;

public record PlatformDashboardResponse(
        Kpis kpis,
        List<TenantSummary> tenants,
        List<Activity> activities,
        ContentHealth contentHealth
) {
    public record Kpis(
            KpiMetric totalTenants,
            KpiMetric totalUsers,
            KpiMetric activeTenants30d,
            KpiMetric certsIssuedToday,
            KpiMetric contentPublished
    ) {}

    public record KpiMetric(
            long value,
            String trend,
            String status
    ) {}

    public record TenantSummary(
            String name,
            String slug,
            String plan,
            long users,
            int completion,
            String status
    ) {}

    public record Activity(
            String icon,
            String color,
            String text,
            String tenant,
            String timeLabel
    ) {}

    public record ContentHealth(
            long publishedTracks,
            long draftTracks,
            long questionCount,
            double avgQuestionsPerSlot,
            String warning
    ) {}
}

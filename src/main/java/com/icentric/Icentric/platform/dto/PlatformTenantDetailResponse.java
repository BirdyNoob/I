package com.icentric.Icentric.platform.dto;

import java.time.LocalDate;
import java.util.List;

public record PlatformTenantDetailResponse(
        TenantInfo tenantInfo,
        Kpis kpis,
        Analytics analytics,
        List<RetrainingRequirement> retrainingRequirements
) {
    public record TenantInfo(
            String name,
            String slug,
            String plan,
            String status
    ) {}

    public record Kpis(
            long totalUsers,
            int completionPercentage,
            long certsIssued,
            long overdueUsers
    ) {}

    public record Analytics(
            List<DepartmentCompletion> departmentCompletion,
            List<WeeklyActivity> weeklyActivity
    ) {}

    public record DepartmentCompletion(
            String label,
            int value
    ) {}

    public record WeeklyActivity(
            String day,
            long logins
    ) {}

    public record RetrainingRequirement(
            String id,
            String title,
            String affectedUsers,
            LocalDate requiredBy,
            String status
    ) {}
}

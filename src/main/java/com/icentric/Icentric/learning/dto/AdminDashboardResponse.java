package com.icentric.Icentric.learning.dto;

import java.util.List;

public record AdminDashboardResponse(

        long totalUsers,
        long totalAssignments,
        long completedAssignments,
        long overdueAssignments,
        long failedAssignments,
        long riskUsersCount,
        double completionRate,
        DashboardTimeInsights timeInsights,
        List<DepartmentStat> departmentStats

) {}

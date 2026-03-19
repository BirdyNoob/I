package com.icentric.Icentric.learning.dto;

public record DepartmentPerformanceResponse(

        String department,
        long totalUsers,
        double completionRate,
        double averageScore

) {}
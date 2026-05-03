package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.common.enums.Department;

public record DepartmentPerformanceResponse(

        Department department,
        long totalUsers,
        double completionRate,
        double averageScore

) {}
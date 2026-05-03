package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.common.enums.Department;

public record DepartmentStat(

        int rank,
        Department department,
        long total,
        long completed,
        double completionRate

) {}

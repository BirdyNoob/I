package com.icentric.Icentric.learning.dto;

public record DepartmentStat(

        int rank,
        String department,
        long total,
        long completed,
        double completionRate

) {}

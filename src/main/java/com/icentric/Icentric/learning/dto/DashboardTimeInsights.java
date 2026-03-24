package com.icentric.Icentric.learning.dto;

public record DashboardTimeInsights(

        long assignmentsLast7Days,
        long quizAttemptsLast7Days,
        long dueNext7Days

) {}

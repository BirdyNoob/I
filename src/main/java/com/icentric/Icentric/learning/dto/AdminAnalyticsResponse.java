package com.icentric.Icentric.learning.dto;

public record AdminAnalyticsResponse(

        long totalUsers,
        long totalAssignments,
        long completedAssignments,
        double completionRate,
        double averageScore

) {}

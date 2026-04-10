package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record ReportRow(
        UUID userId,
        String learnerName,
        String userEmail,
        String role,
        String department,
        String trackId,
        String courseName,
        String status,
        Instant assignedAt,
        Instant dueDate,
        Long daysToDeadline,
        Integer totalLessons,
        Integer completedLessons,
        Integer completionPercent,
        Integer contentVersionAtAssignment,
        Boolean requiresRetraining
) {}

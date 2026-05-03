package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.learning.constants.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminAssignmentSearchResponse(
        UUID assignmentId,
        UUID userId,
        String userName,
        String userEmail,
        Department department,
        UUID trackId,
        String trackTitle,
        Instant assignedAt,
        Instant dueDate,
        AssignmentStatus status,
        Integer contentVersionAtAssignment,
        Boolean requiresRetraining,
        int totalLessons,
        int completedLessons,
        double completionPercent
) {
}

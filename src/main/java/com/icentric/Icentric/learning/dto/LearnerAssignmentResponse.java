package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record LearnerAssignmentResponse(

        UUID assignmentId,
        UUID trackId,
        String trackTitle,
        Instant assignedAt,
        Instant dueDate,
        AssignmentStatus status,
        int totalLessons,
        int completedLessons,
        double completionPercent

) {}

package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record LearnerAssignmentResponse(

        UUID assignmentId,
        UUID trackId,
        String trackTitle,
        Instant assignedAt,
        Instant dueDate,
        String status,
        int totalLessons,
        int completedLessons,
        double completionPercent

) {}

package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record AssessmentResetLogResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        String assessmentConfigId,
        String assessmentTitle,
        UUID managerId,
        String managerName,
        String managerEmail,
        Instant resetAt,
        int attemptsCleared,
        int cumulativeAttemptsSoFar,
        Integer totalAttemptsToComplete,
        boolean isCompleted
) {}

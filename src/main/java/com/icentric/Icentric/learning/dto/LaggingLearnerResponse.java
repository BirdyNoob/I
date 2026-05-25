package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record LaggingLearnerResponse(
        UUID userId,
        String userName,
        String userEmail,
        String department,
        String assessmentConfigId,
        String assessmentTitle,
        int attemptCount,
        int maxAttemptsAllowed,
        Instant lastAttemptDate,
        Integer lastScore
) {}

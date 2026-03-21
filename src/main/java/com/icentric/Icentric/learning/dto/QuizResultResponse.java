package com.icentric.Icentric.learning.dto;

public record QuizResultResponse(
        int score,
        int total,
        boolean passed,
        int attemptNumber,
        int remainingAttempts
) {}

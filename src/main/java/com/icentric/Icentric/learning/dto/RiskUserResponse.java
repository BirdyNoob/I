package com.icentric.Icentric.learning.dto;

import java.util.UUID;

public record RiskUserResponse(

        UUID userId,
        String email,
        double completionPercent,
        double averageScore,
        boolean overdue

) {}
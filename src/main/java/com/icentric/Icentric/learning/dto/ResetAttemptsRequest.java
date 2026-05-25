package com.icentric.Icentric.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ResetAttemptsRequest(
        @NotNull
        UUID userId,
        @NotNull
        String assessmentConfigId
) {}

package com.icentric.Icentric.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RemediationRequest(
        @NotNull
        UUID userId,
        @NotNull
        UUID trackId,
        Instant dueDate
) {}

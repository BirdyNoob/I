package com.icentric.Icentric.learning.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentRequest(

        @NotNull
        UUID userId,
        @NotNull
        UUID trackId,
        @NotNull
        @Future
        Instant dueDate

) {}

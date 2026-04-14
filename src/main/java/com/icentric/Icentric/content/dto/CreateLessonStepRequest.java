package com.icentric.Icentric.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLessonStepRequest(
        @NotBlank String stepType,
        String title,
        String contentJson,
        @NotNull Integer sortOrder
) {}

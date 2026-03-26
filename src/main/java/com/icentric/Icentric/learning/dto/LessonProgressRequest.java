package com.icentric.Icentric.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record LessonProgressRequest(

        @NotNull
        UUID lessonId,
        @NotBlank
        @Pattern(regexp = "IN_PROGRESS|COMPLETED", message = "status must be IN_PROGRESS or COMPLETED")
        String status

) {}

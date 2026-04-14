package com.icentric.Icentric.content.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(

        @NotBlank
        @Size(max = 255)
        String title,
        Integer estimatedMins,
        @NotNull
        @PositiveOrZero
        Integer sortOrder,

        java.util.List<CreateLessonStepRequest> steps

) {}

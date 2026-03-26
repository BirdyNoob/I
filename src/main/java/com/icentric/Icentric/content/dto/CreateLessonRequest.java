package com.icentric.Icentric.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(

        @NotBlank
        @Size(max = 255)
        String title,
        @NotBlank
        @Size(max = 50)
        String lessonType,
        @Size(max = 20000)
        String contentJson,
        @Size(max = 2048)
        String videoUrl,
        @Size(max = 2048)
        String resourceUrl,
        @NotNull
        @PositiveOrZero
        Integer sortOrder

) {}

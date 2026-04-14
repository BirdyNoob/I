package com.icentric.Icentric.content.dto;

import java.util.UUID;

public record LessonStepResponse(
        UUID id,
        UUID lessonId,
        String stepType,
        String title,
        String contentJson,
        int sortOrder
) {}

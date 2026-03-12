package com.icentric.Icentric.content.dto;

import java.util.UUID;

public record LessonResponse(
        UUID id,
        String title,
        String lessonType
) {}

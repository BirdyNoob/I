package com.icentric.Icentric.content.dto;

import com.icentric.Icentric.content.constants.LessonType;
import java.util.UUID;

public record LessonDetailResponse(
        UUID lessonId,
        String title,
        LessonType lessonType,
        String contentJson,
        String videoUrl,
        String resourceUrl
) {}

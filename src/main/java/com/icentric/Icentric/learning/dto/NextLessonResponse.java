package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.content.constants.LessonType;
import java.util.UUID;

public record NextLessonResponse(
        UUID trackId,
        UUID moduleId,
        UUID lessonId,
        String trackTitle,
        String moduleTitle,
        String lessonTitle,
        LessonType lessonType
) {}
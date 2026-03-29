package com.icentric.Icentric.content.dto;

import com.icentric.Icentric.content.constants.LessonType;
import java.util.UUID;

public record LessonResponse(
        UUID id,
        String title,
        LessonType lessonType
) {}

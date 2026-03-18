package com.icentric.Icentric.learning.dto;

import java.util.UUID;

public record NextLessonResponse(

        UUID trackId,
        UUID moduleId,
        UUID lessonId,
        String trackTitle,
        String moduleTitle,
        String lessonTitle,
        String lessonType

) {}
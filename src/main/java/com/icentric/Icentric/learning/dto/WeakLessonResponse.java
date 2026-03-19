package com.icentric.Icentric.learning.dto;

import java.util.UUID;

public record WeakLessonResponse(

        UUID lessonId,
        String lessonTitle,
        double averageScore,
        long attempts

) {}
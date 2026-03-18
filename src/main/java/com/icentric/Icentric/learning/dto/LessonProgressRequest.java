package com.icentric.Icentric.learning.dto;

import java.util.UUID;

public record LessonProgressRequest(

        UUID lessonId,
        String status

) {}

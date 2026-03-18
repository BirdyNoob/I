package com.icentric.Icentric.content.dto;


import java.util.UUID;

public record LessonDetailResponse(

        UUID lessonId,
        String title,
        String lessonType,
        String contentJson,
        String videoUrl,
        String resourceUrl

) {}

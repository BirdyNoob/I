package com.icentric.Icentric.content.dto;

public record CreateLessonRequest(

        String title,
        String lessonType,
        String contentJson,
        String videoUrl,
        String resourceUrl,
        Integer sortOrder

) {}

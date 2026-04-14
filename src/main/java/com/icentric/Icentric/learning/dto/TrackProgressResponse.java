package com.icentric.Icentric.learning.dto;


import java.util.List;
import java.util.UUID;

public record TrackProgressResponse(
        UUID trackId,
        String trackTitle,
        int totalLessons,
        int completedLessons,
        int progressPercent,
        List<ModuleProgress> modules
) {
    public record ModuleProgress(
            UUID moduleId,
            String moduleTitle,
            int sortOrder,
            boolean completed,
            List<LessonStatus> lessons
    ) {}

    public record LessonStatus(
            UUID lessonId,
            String title,
            int sortOrder,
            boolean completed,
            boolean locked
    ) {}
}

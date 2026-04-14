package com.icentric.Icentric.learning.dto;

import java.util.List;
import java.util.UUID;

public record LearningPathResponse(
        UUID trackId,
        String title,
        String version,
        String description,
        boolean mandatory,
        String estimatedMinutesLabel,
        int completedModules,
        int totalModules,
        String remainingMinutesLabel,
        String deadlineLabel,
        String daysRemainingLabel,
        List<TimelineItem> timeline,
        List<ModuleItem> modules
) {
    public record TimelineItem(
            UUID moduleId,
            String title,
            String label,
            String status
    ) {}

    public record ModuleItem(
            UUID moduleId,
            int moduleNumber,
            String title,
            String status,
            List<String> topics,
            String meta,
            Integer scorePercent,
            List<LessonItem> lessons,
            Integer progressPercent,
            Integer completedSteps,
            Integer totalSteps
    ) {}

    public record LessonItem(
            UUID lessonId,
            String title,
            String status,
            String meta,
            String actionLabel
    ) {}
}

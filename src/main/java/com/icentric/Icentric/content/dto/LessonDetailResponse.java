package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

public record LessonDetailResponse(
        UUID id,
        String moduleTitle,
        String lessonTitle,
        Integer estimatedTimeLeftMinutes,
        Integer totalSteps,
        UUID currentStepId,
        List<OutlineItem> outline
) {
    public record OutlineItem(
        UUID id,
        String type,
        String title,
        String durationFormatted,
        boolean isCompleted
    ) {}
}

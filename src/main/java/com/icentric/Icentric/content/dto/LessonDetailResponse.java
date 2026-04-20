package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

/**
 * Returned by GET /api/v1/lessons/{lessonId}.
 *
 * Intentionally lightweight: no step content payloads are embedded here.
 * The frontend uses the outline to render the sidebar and tracks which step
 * to load next. Content for each step is fetched separately on demand via
 * GET /api/v1/lessons/{lessonId}/steps/{stepId}.
 */
public record LessonDetailResponse(
        UUID id,
        String moduleTitle,
        String lessonTitle,
        Integer estimatedTimeLeftMinutes,
        Integer totalSteps,
        /** UUID of the first incomplete step — the frontend should load this first. */
        UUID currentStepId,
        /** Sidebar outline items — type, title, duration, isCompleted only. */
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


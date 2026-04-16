package com.icentric.Icentric.content.dto;

import java.util.UUID;

/**
 * Returned for both the outline list (inside LessonDetailResponse)
 * and the individual step fetch endpoint (GET /lessons/{id}/steps/{stepId}).
 *
 * The raw `contentJson` field holds a serialized JSON object whose
 * schema depends on `stepType`:
 *  - CONCEPT  → { title, description, keyPoints[], principle, keyInsight }
 *  - SCENARIO → { badgeText, contextLabel, scenarioText, options[] }
 *  - QUIZ     → { title, questions[] }
 *  - DO_DONT  → { title, description, dos[], donts[] }
 *  - SUMMARY  → { title, points[], ultraShort, outcome[] }
 */
public record LessonStepResponse(
        UUID id,
        UUID lessonId,
        String stepType,
        String title,
        String durationFormatted,
        boolean isCompleted,
        int sortOrder,
        /** Full step payload — only populated on the single-step endpoint, null in outline. */
        String contentJson
) {}

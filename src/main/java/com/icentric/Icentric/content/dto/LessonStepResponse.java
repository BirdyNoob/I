package com.icentric.Icentric.content.dto;

import java.util.UUID;

/**
 * Returned by GET /api/v1/lessons/{lessonId}/steps/{stepId}.
 *
 * The `data` field is a fully parsed JSON object (not a raw string) whose
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
        /** Fully deserialized content object — the frontend receives structured JSON, not a string. */
        Object data
) {}


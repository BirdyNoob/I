package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

public record LessonResponse(
        UUID id,
        String title,
        Integer estimatedMins,
        Integer sortOrder,
        Boolean isPublished,
        List<LessonStepResponse> steps
) {}

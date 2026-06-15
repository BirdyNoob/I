package com.icentric.Icentric.content.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrackDetailResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String department,
        String courseType,
        Integer estimatedMins,
        Boolean isPublished,
        Boolean isMandatory,
        String status,
        Integer version,
        UUID previousVersionId,
        Instant createdAt,
        Instant publishedAt,
        List<ModuleResponse> modules
) {}

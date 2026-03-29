package com.icentric.Icentric.content.dto;

import java.time.Instant;
import java.util.UUID;

public record TrackVersionResponse(
        UUID id,
        String slug,
        String title,
        Integer version,
        String status,
        Boolean isPublished,
        Instant createdAt,
        Instant publishedAt,
        UUID previousVersionId,
        String changeSummary
) {}

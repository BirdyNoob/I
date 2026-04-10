package com.icentric.Icentric.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        long memberCount,
        Instant createdAt
) {
}

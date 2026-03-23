package com.icentric.Icentric.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(

        UUID id,
        String name,
        String slug,
        Instant createdAt

) {}
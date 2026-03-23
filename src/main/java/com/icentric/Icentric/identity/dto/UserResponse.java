package com.icentric.Icentric.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        String department,
        boolean isActive,
        Instant createdAt
) {}

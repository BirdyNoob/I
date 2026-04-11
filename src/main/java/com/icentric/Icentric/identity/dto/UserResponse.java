package com.icentric.Icentric.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String location,
        String role,
        String department,
        boolean isActive,
        Instant createdAt,
        Instant lastLogin
) {}

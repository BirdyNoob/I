package com.icentric.Icentric.identity.dto;

import com.icentric.Icentric.common.enums.Department;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String location,
        String role,
        Department department,
        boolean isActive,
        Instant createdAt,
        Instant lastLogin
) {}

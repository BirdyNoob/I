package com.icentric.Icentric.identity.dto;

import com.icentric.Icentric.common.enums.Department;

import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String name,
        String email,
        String role,
        Department department,
        boolean isActive
) {
}

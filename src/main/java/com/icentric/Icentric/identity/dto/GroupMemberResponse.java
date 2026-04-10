package com.icentric.Icentric.identity.dto;

import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String name,
        String email,
        String role,
        String department,
        boolean isActive
) {
}

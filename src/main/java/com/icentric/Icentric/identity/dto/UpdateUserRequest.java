package com.icentric.Icentric.identity.dto;

public record UpdateUserRequest(
        String role,
        String department,
        Boolean isActive
) {}
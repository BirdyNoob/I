package com.icentric.Icentric.identity.dto;

public record BulkUploadRowDto(
        String name,
        String email,
        String role,
        String department
) {}

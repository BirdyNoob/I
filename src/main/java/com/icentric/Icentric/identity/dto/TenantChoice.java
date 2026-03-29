package com.icentric.Icentric.identity.dto;

import java.util.UUID;

/**
 * Represents a single workspace/tenant a user belongs to.
 * Returned when the user needs to pick their workspace after login.
 */
public record TenantChoice(

        UUID tenantId,
        String slug,
        String companyName,
        String role

) {}

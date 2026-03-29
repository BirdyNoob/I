package com.icentric.Icentric.identity.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload sent after the user picks a workspace from the multi-tenant list.
 */
public record SelectTenantRequest(

        @NotNull UUID tenantId

) {}

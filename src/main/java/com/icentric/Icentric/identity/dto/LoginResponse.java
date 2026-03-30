package com.icentric.Icentric.identity.dto;

import java.util.List;

/**
 * Login response that supports two scenarios:
 *
 * <ol>
 *   <li><b>Single tenant</b> — {@code accessToken} and {@code refreshToken} are populated,
 *       {@code tenants} is {@code null}.</li>
 *   <li><b>Multiple tenants</b> — tokens are {@code null}, {@code tenants} contains
 *       the workspaces the user can choose from.</li>
 * </ol>
 */
public record LoginResponse(

        /** Non-null when a single tenant was auto-selected. */
        String accessToken,

        /** Non-null when a single tenant was auto-selected. */
        String refreshToken,

        /** Non-null when tokens are issued for a selected tenant. */
        String role,

        /** Non-null when the user belongs to multiple tenants and must choose. */
        List<TenantChoice> tenants

) {
    /** Convenience factory for the single-tenant happy path. */
    public static LoginResponse singleTenant(String accessToken, String refreshToken, String role) {
        return new LoginResponse(accessToken, refreshToken, role, null);
    }

    /** Convenience factory for the multi-tenant choice response. */
    public static LoginResponse multiTenant(List<TenantChoice> tenants) {
        return new LoginResponse(null, null, null, tenants);
    }
}

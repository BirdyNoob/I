package com.icentric.Icentric.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Stateless utility for reading the authenticated user's identity from the
 * Spring Security context.
 *
 * <p>Centralises the repeated {@code SecurityContextHolder → getDetails → UUID.fromString}
 * pattern that was previously duplicated across ~13 services and controllers.</p>
 *
 * <p>Thread-safe: relies entirely on Spring's own {@link SecurityContextHolder}
 * which is backed by a thread-local by default.</p>
 */
public final class SecurityUtils {

    private SecurityUtils() { /* utility class – no instances */ }

    /**
     * Returns the UUID of the currently authenticated user, or {@code null}
     * if the request is unauthenticated or the details cannot be parsed.
     *
     * @return current user UUID, or {@code null}
     */
    public static UUID currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getDetails().toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Returns the UUID of the currently authenticated user.
     *
     * @throws IllegalStateException if the request is unauthenticated
     */
    public static UUID currentUserId() {
        UUID id = currentUserIdOrNull();
        if (id == null) {
            throw new IllegalStateException("Unauthenticated request");
        }
        return id;
    }
}

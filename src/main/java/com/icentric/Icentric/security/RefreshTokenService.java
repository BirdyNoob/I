package com.icentric.Icentric.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates, validates, and revokes opaque refresh tokens.
 * Currently backed by PostgreSQL; swap to Redis later by implementing
 * the same methods against a RedisTemplate.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long ttlDays;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${icentric.jwt.refresh-ttl-days:7}") long ttlDays) {
        this.repository = repository;
        this.ttlDays = ttlDays;
    }

    /**
     * Create and persist a new refresh token.
     */
    @Transactional
    public String create(UUID userId, String email, String role, String tenantSlug) {
        String token = generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS);

        RefreshToken entity = new RefreshToken(token, userId, email, role, tenantSlug, expiresAt);
        repository.save(entity);
        return token;
    }

    /**
     * Validate and return the stored token. Throws if missing, revoked, or expired.
     */
    @Transactional(readOnly = true)
    public RefreshToken validate(String token) {
        RefreshToken stored = repository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }
        return stored;
    }

    /**
     * Revoke a single refresh token (logout).
     */
    @Transactional
    public void revoke(String token) {
        repository.findByTokenAndRevokedFalse(token)
                .ifPresent(rt -> {
                    rt.revoke();
                    repository.save(rt);
                });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.icentric.Icentric.security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * DB-backed refresh token. Lives in the system schema.
 * Swap to Redis for scale later — the service interface stays the same.
 */
@Entity
@Table(name = "refresh_tokens", schema = "system")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "tenant_slug", nullable = false, length = 63)
    private String tenantSlug;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(String token, UUID userId, String email, String role,
                        String tenantSlug, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.tenantSlug = tenantSlug;
        this.expiresAt = expiresAt;
    }

    public UUID getId()           { return id; }
    public String getToken()      { return token; }
    public UUID getUserId()       { return userId; }
    public String getEmail()      { return email; }
    public String getRole()       { return role; }
    public String getTenantSlug() { return tenantSlug; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked()    { return revoked; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke() { this.revoked = true; }
}

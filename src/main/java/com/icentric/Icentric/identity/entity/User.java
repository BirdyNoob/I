package com.icentric.Icentric.identity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Global user record.
 * Lives in the {@code system} schema — resolved independently of the
 * per-tenant {@code search_path}.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "users", schema = "system")
public class User {

    @Id
    private UUID id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** LOCAL | GOOGLE | MICROSOFT */
    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "LOCAL";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}

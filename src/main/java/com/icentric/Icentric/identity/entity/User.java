package com.icentric.Icentric.identity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    private String name;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String role;

    private String department;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "impersonated_by")
    private UUID impersonatedBy;

    @Column(name = "impersonation_expires_at")
    private Instant impersonationExpiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}

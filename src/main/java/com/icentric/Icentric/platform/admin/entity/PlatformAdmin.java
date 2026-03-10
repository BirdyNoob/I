package com.icentric.Icentric.platform.admin.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "platform_admins", schema = "system")
public class PlatformAdmin {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String fullName;

    private Boolean isActive;

    private Boolean mfaEnabled;

    private Instant lastLoginAt;

    private Instant createdAt;
    @Column(name = "mfa_secret")
    private String mfaSecret;

    public PlatformAdmin() {}

    public PlatformAdmin(String email, String passwordHash, String fullName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    // getters setters
}

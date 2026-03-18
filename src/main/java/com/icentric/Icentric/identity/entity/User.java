package com.icentric.Icentric.identity.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String role;

    private String department;

    private Boolean isActive;

    private UUID impersonatedBy;

    private Instant impersonationExpiresAt;

    private Instant createdAt;

    private Instant lastLoginAt;

    public User() {}

    // getters setters
}
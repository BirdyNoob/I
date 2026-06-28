package com.icentric.Icentric.security;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "token_blacklist", schema = "system")
public class BlacklistedToken {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

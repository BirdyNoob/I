package com.icentric.Icentric.platform.impersonation.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "impersonation_sessions", schema = "system")
public class ImpersonationSession {

    @Id
    private UUID id;

    private UUID platformAdminId;

    private UUID impersonatedUserId;

    private String tenantSlug;

    private String reason;

    private Instant startedAt;

    private Instant endedAt;

    private Integer actionsTaken;

    public ImpersonationSession() {
        this.id = UUID.randomUUID();
        this.startedAt = Instant.now();
        this.actionsTaken = 0;
    }

    // getters setters
}

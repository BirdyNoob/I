package com.icentric.Icentric.audit.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "audit_logs", schema = "system")
public class AuditLog {

    @Id
    private UUID id;

    private UUID userId;
    
    private String tenantSlug;

    private String action;     // LOGIN, UPDATE_TRACK, ASSIGN_TRACK
    private String entityType; // TRACK, USER, QUIZ
    private String entityId;

    private String details;    // JSON/string

    private Instant createdAt;
}

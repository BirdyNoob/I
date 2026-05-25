package com.icentric.Icentric.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "module_progress")
public class ModuleProgress {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(nullable = false)
    private String status; // IN_PROGRESS, COMPLETED

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "spent_seconds")
    private Integer spentSeconds = 0;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    public ModuleProgress() {}
}

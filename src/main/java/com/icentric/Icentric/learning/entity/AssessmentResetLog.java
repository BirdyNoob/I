package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "assessment_reset_logs")
public class AssessmentResetLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assessment_config_id", nullable = false)
    private String assessmentConfigId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "reset_at", nullable = false)
    private Instant resetAt;

    @Column(name = "attempts_count", nullable = false)
    private Integer attemptsCount;
}

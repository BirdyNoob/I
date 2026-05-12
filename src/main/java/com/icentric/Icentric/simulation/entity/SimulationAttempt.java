package com.icentric.Icentric.simulation.entity;

import com.icentric.Icentric.simulation.constants.RiskCategory;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "simulation_attempts")
public class SimulationAttempt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "user_response", columnDefinition = "TEXT")
    private String userResponse;

    @Column(name = "final_score")
    private Integer finalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category")
    private RiskCategory riskCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rules", columnDefinition = "jsonb")
    private JsonNode triggeredRules;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private Instant createdAt;
    private Instant updatedAt;
}

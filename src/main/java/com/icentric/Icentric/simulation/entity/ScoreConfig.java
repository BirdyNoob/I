package com.icentric.Icentric.simulation.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "simulation_score_configs", schema = "system")
public class ScoreConfig {

    @Id
    private UUID id;

    @Column(name = "simulation_id", nullable = false, unique = true)
    private UUID simulationId;

    @Column(name = "base_score", nullable = false)
    private Integer baseScore;

    @Column(name = "critical_threshold", nullable = false)
    private Integer criticalThreshold;

    @Column(name = "high_threshold", nullable = false)
    private Integer highThreshold;

    private Instant createdAt;
    private Instant updatedAt;
}

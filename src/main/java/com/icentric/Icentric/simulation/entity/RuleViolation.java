package com.icentric.Icentric.simulation.entity;

import com.icentric.Icentric.simulation.constants.RuleType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "simulation_rule_violations")
public class RuleViolation {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(name = "penalty_applied", nullable = false)
    private Integer penaltyApplied;

    private Instant createdAt;
}

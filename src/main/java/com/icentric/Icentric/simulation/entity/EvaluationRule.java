package com.icentric.Icentric.simulation.entity;

import com.icentric.Icentric.simulation.constants.RuleType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "simulation_evaluation_rules", schema = "system")
public class EvaluationRule {

    @Id
    private UUID id;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(name = "rule_pattern", nullable = false, columnDefinition = "TEXT")
    private String rulePattern;

    @Column(name = "penalty_points", nullable = false)
    private Integer penaltyPoints;

    @Column(name = "feedback_text", nullable = false, columnDefinition = "TEXT")
    private String feedbackText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private Instant createdAt;
    private Instant updatedAt;
}

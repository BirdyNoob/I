package com.icentric.Icentric.simulation.entity;

import com.icentric.Icentric.simulation.constants.SimulationDifficultyLevel;
import com.icentric.Icentric.simulation.constants.SimulationType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "simulations", schema = "system")
public class Simulation {

    @Id
    private UUID id;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_type", nullable = false)
    private SimulationType simulationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false)
    private SimulationDifficultyLevel difficultyLevel;

    @Column(name = "scenario_prompt", nullable = false, columnDefinition = "TEXT")
    private String scenarioPrompt;

    @Column(name = "estimated_mins", nullable = false)
    private Integer estimatedMins;

    @Column(nullable = false)
    private Boolean published;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private JsonNode configJson;

    private Instant createdAt;
    private Instant updatedAt;
}

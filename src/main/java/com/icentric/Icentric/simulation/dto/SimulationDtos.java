package com.icentric.Icentric.simulation.dto;

import com.icentric.Icentric.simulation.constants.RuleType;
import com.icentric.Icentric.simulation.constants.SimulationDifficultyLevel;
import com.icentric.Icentric.simulation.constants.SimulationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SimulationDtos {

    @Data
    public static class CreateSimulationRequest {
        @NotNull
        private UUID trackId;
        @NotBlank
        private String title;
        private String description;
        @NotNull
        private SimulationType simulationType;
        @NotNull
        private SimulationDifficultyLevel difficultyLevel;
        @NotBlank
        private String scenarioPrompt;
        @NotNull @Min(1)
        private Integer estimatedMins;
    }

    @Data
    public static class AddRuleRequest {
        @NotNull
        private RuleType ruleType;
        @NotBlank
        private String rulePattern;
        @NotNull @Min(0)
        private Integer penaltyPoints;
        @NotBlank
        private String feedbackText;
    }
    
    @Data
    public static class UpdateScoreConfigRequest {
        @NotNull @Min(1)
        private Integer baseScore;
        @NotNull @Min(0)
        private Integer criticalThreshold;
        @NotNull @Min(0)
        private Integer highThreshold;
    }

    @Data
    public static class SubmitResponseRequest {
        @NotBlank
        private String userResponse;
    }

    @Data
    public static class SimulationAdminResponse {
        private UUID id;
        private String title;
        private Boolean published;
        private Instant createdAt;
    }

    @Data
    public static class LearnerSimulationView {
        private UUID simulationId;
        private String title;
        private String scenarioPrompt;
        private SimulationDifficultyLevel difficultyLevel;
    }
}

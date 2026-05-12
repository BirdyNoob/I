package com.icentric.Icentric.simulation.service;

import com.icentric.Icentric.simulation.constants.RiskCategory;
import com.icentric.Icentric.simulation.entity.EvaluationRule;
import com.icentric.Icentric.simulation.entity.ScoreConfig;
import com.icentric.Icentric.simulation.repository.ScoreConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationScoringService {

    private final ScoreConfigRepository scoreConfigRepository;

    public ScoringResult calculateScoreAndRisk(UUID simulationId, List<EvaluationRule> triggeredRules) {
        ScoreConfig config = scoreConfigRepository.findBySimulationId(simulationId)
                .orElseGet(() -> defaultScoreConfig(simulationId));

        int totalPenalty = triggeredRules.stream()
                .mapToInt(EvaluationRule::getPenaltyPoints)
                .sum();

        int finalScore = Math.max(0, config.getBaseScore() - totalPenalty);

        RiskCategory riskCategory;
        if (finalScore <= config.getCriticalThreshold()) {
            riskCategory = RiskCategory.CRITICAL;
        } else if (finalScore <= config.getHighThreshold()) {
            riskCategory = RiskCategory.HIGH;
        } else if (finalScore < config.getBaseScore()) {
            riskCategory = RiskCategory.MEDIUM;
        } else {
            riskCategory = RiskCategory.SAFE;
        }

        return new ScoringResult(finalScore, riskCategory);
    }

    private ScoreConfig defaultScoreConfig(UUID simulationId) {
        ScoreConfig config = new ScoreConfig();
        config.setSimulationId(simulationId);
        config.setBaseScore(100);
        config.setCriticalThreshold(50);
        config.setHighThreshold(70);
        return config;
    }

    public record ScoringResult(int finalScore, RiskCategory riskCategory) {}
}

package com.icentric.Icentric.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.simulation.constants.RiskCategory;
import com.icentric.Icentric.simulation.entity.EvaluationRule;
import com.icentric.Icentric.simulation.entity.RuleViolation;
import com.icentric.Icentric.simulation.entity.SimulationAttempt;
import com.icentric.Icentric.simulation.repository.RuleViolationRepository;
import com.icentric.Icentric.simulation.repository.SimulationAttemptRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationAttemptService {

    private final SimulationAttemptRepository attemptRepository;
    private final RuleViolationRepository violationRepository;
    private final RuleEvaluationService ruleEvaluationService;
    private final SimulationScoringService scoringService;
    private final FeedbackService feedbackService;
    private final RemediationService remediationService;
    private final ObjectMapper objectMapper;
    private final TenantSchemaService tenantSchemaService;

    /**
     * Creates a new IN_PROGRESS attempt for a learner.
     * Must be @Transactional so applyCurrentTenantSearchPath() and the INSERT
     * share the same DB connection/search_path.
     */
    @Transactional
    public UUID startAttempt(UUID simulationId, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        SimulationAttempt attempt = new SimulationAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setSimulationId(simulationId);
        attempt.setUserId(userId);
        attempt.setStartedAt(Instant.now());
        attempt.setStatus("IN_PROGRESS");
        attempt.setCreatedAt(Instant.now());
        attempt.setUpdatedAt(Instant.now());

        return attemptRepository.save(attempt).getId();
    }

    @Transactional
    public EvaluationResultResponse submitResponse(UUID attemptId, String userResponse) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        SimulationAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));

        if ("COMPLETED".equals(attempt.getStatus())) {
            throw new IllegalStateException("Attempt already completed");
        }

        UUID simulationId = attempt.getSimulationId();

        // 1. Rule Engine Evaluation
        List<EvaluationRule> triggeredRules = ruleEvaluationService.evaluateResponse(simulationId, userResponse);

        // 2. Scoring Engine
        SimulationScoringService.ScoringResult scoringResult = scoringService.calculateScoreAndRisk(simulationId, triggeredRules);

        // 3. Feedback Engine
        String feedback = feedbackService.generateFeedback(triggeredRules);

        // Update Attempt
        attempt.setUserResponse(userResponse);
        attempt.setStatus("COMPLETED");
        attempt.setCompletedAt(Instant.now());
        attempt.setFinalScore(scoringResult.finalScore());
        attempt.setRiskCategory(scoringResult.riskCategory());
        
        try {
            List<UUID> ruleIds = triggeredRules.stream().map(EvaluationRule::getId).toList();
            attempt.setTriggeredRules(objectMapper.valueToTree(ruleIds));
        } catch (Exception ignored) {}

        attemptRepository.save(attempt);

        // Save Violations for Analytics
        for (EvaluationRule rule : triggeredRules) {
            RuleViolation violation = new RuleViolation();
            violation.setId(UUID.randomUUID());
            violation.setAttemptId(attempt.getId());
            violation.setSimulationId(simulationId);
            violation.setRuleId(rule.getId());
            violation.setRuleType(rule.getRuleType());
            violation.setPenaltyApplied(rule.getPenaltyPoints());
            violation.setCreatedAt(Instant.now());
            violationRepository.save(violation);
        }

        // 4. Remediation Engine
        if (scoringResult.riskCategory() == RiskCategory.CRITICAL) {
            remediationService.triggerMandatoryRetraining(attempt.getUserId(), simulationId);
        }

        return new EvaluationResultResponse(
                scoringResult.finalScore(),
                scoringResult.riskCategory().name(),
                feedback
        );
    }

    public record EvaluationResultResponse(int score, String riskCategory, String feedback) {}
}

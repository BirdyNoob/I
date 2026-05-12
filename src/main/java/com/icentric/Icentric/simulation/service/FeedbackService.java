package com.icentric.Icentric.simulation.service;

import com.icentric.Icentric.simulation.entity.EvaluationRule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    public String generateFeedback(List<EvaluationRule> triggeredRules) {
        if (triggeredRules.isEmpty()) {
            return "Great job! Your response did not trigger any security or safety violations.";
        }

        return triggeredRules.stream()
                .map(rule -> "- " + rule.getFeedbackText())
                .collect(Collectors.joining("\n"));
    }
}

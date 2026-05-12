package com.icentric.Icentric.simulation.service;

import com.icentric.Icentric.simulation.constants.RuleType;
import com.icentric.Icentric.simulation.entity.EvaluationRule;
import com.icentric.Icentric.simulation.repository.EvaluationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RuleEvaluationService {

    private final EvaluationRuleRepository ruleRepository;

    public List<EvaluationRule> evaluateResponse(UUID simulationId, String userResponse) {
        List<EvaluationRule> rules = ruleRepository.findBySimulationIdOrderBySortOrderAsc(simulationId);
        List<EvaluationRule> triggeredRules = new ArrayList<>();

        if (userResponse == null || userResponse.trim().isEmpty()) {
            return triggeredRules;
        }

        for (EvaluationRule rule : rules) {
            if (isRuleTriggered(rule, userResponse)) {
                triggeredRules.add(rule);
            }
        }

        return triggeredRules;
    }

    private boolean isRuleTriggered(EvaluationRule rule, String userResponse) {
        String pattern = rule.getRulePattern();
        
        return switch (rule.getRuleType()) {
            case REGEX -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(userResponse).find();
            case KEYWORD_EXACT -> userResponse.toLowerCase().contains(pattern.toLowerCase());
            case KEYWORD_FUZZY -> {
                String[] keywords = pattern.split(",");
                boolean found = false;
                for (String kw : keywords) {
                    if (userResponse.toLowerCase().contains(kw.trim().toLowerCase())) {
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case LENGTH_CHECK -> {
                try {
                    int minLength = Integer.parseInt(pattern);
                    yield userResponse.length() < minLength;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> false;
        };
    }
}

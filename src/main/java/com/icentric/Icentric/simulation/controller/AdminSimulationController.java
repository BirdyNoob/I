package com.icentric.Icentric.simulation.controller;

import com.icentric.Icentric.simulation.dto.SimulationDtos;
import com.icentric.Icentric.simulation.entity.EvaluationRule;
import com.icentric.Icentric.simulation.entity.ScoreConfig;
import com.icentric.Icentric.simulation.entity.Simulation;
import com.icentric.Icentric.simulation.repository.EvaluationRuleRepository;
import com.icentric.Icentric.simulation.repository.ScoreConfigRepository;
import com.icentric.Icentric.simulation.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/simulations")
@RequiredArgsConstructor
public class AdminSimulationController {

    private final SimulationService simulationService;
    private final EvaluationRuleRepository ruleRepository;
    private final ScoreConfigRepository scoreConfigRepository;
    
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public java.util.List<Simulation> getAll() {
        return simulationService.getAllSimulations();
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public SimulationDtos.SimulationAdminResponse create(@Valid @RequestBody SimulationDtos.CreateSimulationRequest request) {
        Simulation simulation = new Simulation();
        simulation.setId(UUID.randomUUID());
        simulation.setTrackId(request.getTrackId());
        simulation.setTitle(request.getTitle());
        simulation.setDescription(request.getDescription());
        simulation.setSimulationType(request.getSimulationType());
        simulation.setDifficultyLevel(request.getDifficultyLevel());
        simulation.setScenarioPrompt(request.getScenarioPrompt());
        simulation.setEstimatedMins(request.getEstimatedMins());
        simulation.setPublished(false);
        simulation.setCreatedAt(Instant.now());
        simulation.setUpdatedAt(Instant.now());

        Simulation saved = simulationService.createSimulation(simulation);

        SimulationDtos.SimulationAdminResponse response = new SimulationDtos.SimulationAdminResponse();
        response.setId(saved.getId());
        response.setTitle(saved.getTitle());
        response.setPublished(saved.getPublished());
        response.setCreatedAt(saved.getCreatedAt());
        return response;
    }

    @PostMapping("/{id}/rules")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public EvaluationRule addRule(@PathVariable UUID id, @Valid @RequestBody SimulationDtos.AddRuleRequest request) {
        EvaluationRule rule = new EvaluationRule();
        rule.setId(UUID.randomUUID());
        rule.setSimulationId(id);
        rule.setRuleType(request.getRuleType());
        rule.setRulePattern(request.getRulePattern());
        rule.setPenaltyPoints(request.getPenaltyPoints());
        rule.setFeedbackText(request.getFeedbackText());
        rule.setSortOrder(0); // For now, simple append
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        return ruleRepository.save(rule);
    }

    @PostMapping("/{id}/score-config")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ScoreConfig updateScoreConfig(@PathVariable UUID id, @Valid @RequestBody SimulationDtos.UpdateScoreConfigRequest request) {
        ScoreConfig config = scoreConfigRepository.findBySimulationId(id).orElseGet(ScoreConfig::new);
        if (config.getId() == null) {
            config.setId(UUID.randomUUID());
            config.setSimulationId(id);
            config.setCreatedAt(Instant.now());
        }
        config.setBaseScore(request.getBaseScore());
        config.setCriticalThreshold(request.getCriticalThreshold());
        config.setHighThreshold(request.getHighThreshold());
        config.setUpdatedAt(Instant.now());
        return scoreConfigRepository.save(config);
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public SimulationDtos.SimulationAdminResponse publish(@PathVariable UUID id) {
        Simulation sim = simulationService.getSimulation(id);
        sim.setPublished(true);
        sim.setUpdatedAt(Instant.now());
        
        sim = simulationService.updateSimulation(sim);
        
        SimulationDtos.SimulationAdminResponse response = new SimulationDtos.SimulationAdminResponse();
        response.setId(sim.getId());
        response.setTitle(sim.getTitle());
        response.setPublished(sim.getPublished());
        return response;
    }
}

package com.icentric.Icentric.simulation.controller;

import com.icentric.Icentric.common.security.SecurityUtils;
import com.icentric.Icentric.simulation.dto.*;
import com.icentric.Icentric.simulation.service.SimulationService;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping
    public ResponseEntity<SimulationListResponse> listSimulations() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(simulationService.listSimulations(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LearnerSimulationDetailResponse> getSimulation(@PathVariable String id) {
        return ResponseEntity.ok(simulationService.getSimulationForLearner(id));
    }

    @GetMapping("/{id}/review")
    public ResponseEntity<SimulationReviewResponse> reviewSimulation(@PathVariable String id) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(simulationService.reviewSimulation(id, userId));
    }

    @PostMapping("/{id}/scenes/{sceneId}/answer")
    public ResponseEntity<SceneAnswerResponse> answerScene(
            @PathVariable String id,
            @PathVariable String sceneId,
            @RequestBody SceneAnswerRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(simulationService.answerScene(id, sceneId, request.getAnswer(), userId));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<SimulationResultResponse> submitSimulation(
            @PathVariable String id,
            @RequestBody SimulationSubmitRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        String tenantSlug = TenantContext.getTenant();
        return ResponseEntity.ok(simulationService.submitAnswers(id, request, userId, tenantSlug));
    }
}

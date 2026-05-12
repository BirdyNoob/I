package com.icentric.Icentric.simulation.controller;

import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.simulation.dto.SimulationDtos;
import com.icentric.Icentric.simulation.entity.Simulation;
import com.icentric.Icentric.simulation.service.SimulationAttemptService;
import com.icentric.Icentric.simulation.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learner/simulations")
@RequiredArgsConstructor
public class LearnerSimulationController {

    private final SimulationService simulationService;
    private final SimulationAttemptService attemptService;
    private final UserRepository userRepository;

    // ── GET /api/v1/learner/simulations ──────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('LEARNER')")
    public List<Simulation> getAvailable() {
        return simulationService.getPublishedSimulations();
    }

    // ── GET /api/v1/learner/simulations/{id} ─────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('LEARNER')")
    public SimulationDtos.LearnerSimulationView getSimulation(@PathVariable UUID id) {
        Simulation sim = simulationService.getSimulation(id);

        SimulationDtos.LearnerSimulationView view = new SimulationDtos.LearnerSimulationView();
        view.setSimulationId(sim.getId());
        view.setTitle(sim.getTitle());
        view.setScenarioPrompt(sim.getScenarioPrompt());
        view.setDifficultyLevel(sim.getDifficultyLevel());
        return view;
    }

    // ── POST /api/v1/learner/simulations/{id}/start ──────────────────────────
    // Returns the attemptId (UUID) — frontend must store this for submit.
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('LEARNER')")
    public UUID startSimulation(@PathVariable UUID id, @AuthenticationPrincipal String userEmail) {
        Simulation sim = simulationService.getSimulation(id);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        // Delegate to @Transactional service method — search_path + INSERT share one transaction
        return attemptService.startAttempt(sim.getId(), user.getId());
    }

    // ── POST /api/v1/learner/simulations/attempts/{attemptId}/submit ─────────
    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('LEARNER')")
    public SimulationAttemptService.EvaluationResultResponse submitResponse(
            @PathVariable UUID attemptId,
            @Valid @RequestBody SimulationDtos.SubmitResponseRequest request) {

        return attemptService.submitResponse(attemptId, request.getUserResponse());
    }
}

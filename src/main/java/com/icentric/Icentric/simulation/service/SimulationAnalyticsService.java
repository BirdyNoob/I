package com.icentric.Icentric.simulation.service;

import com.icentric.Icentric.simulation.repository.RuleViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationAnalyticsService {
    
    private final RuleViolationRepository violationRepository;

    public long getTotalViolationsForSimulation(UUID simulationId) {
        return violationRepository.findBySimulationId(simulationId).size();
    }
}

package com.icentric.Icentric.simulation.service;

import com.icentric.Icentric.simulation.entity.Simulation;
import com.icentric.Icentric.simulation.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationService {
    
    private final SimulationRepository simulationRepository;

    public Simulation createSimulation(Simulation simulation) {
        return simulationRepository.save(simulation);
    }

    public Simulation updateSimulation(Simulation simulation) {
        return simulationRepository.save(simulation);
    }

    public Simulation getSimulation(UUID id) {
        return simulationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));
    }

    public List<Simulation> getSimulationsForTrack(UUID trackId) {
        return simulationRepository.findByTrackId(trackId);
    }

    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    public List<Simulation> getPublishedSimulations() {
        return simulationRepository.findByPublishedTrue();
    }
}

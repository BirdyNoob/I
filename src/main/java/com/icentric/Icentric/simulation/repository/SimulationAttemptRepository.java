package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.SimulationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SimulationAttemptRepository extends JpaRepository<SimulationAttempt, UUID> {
    List<SimulationAttempt> findByUserIdAndSimulationId(UUID userId, UUID simulationId);
}

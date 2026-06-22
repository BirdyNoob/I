package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    Optional<Simulation> findBySimId(String simId);
    boolean existsBySimId(String simId);
}

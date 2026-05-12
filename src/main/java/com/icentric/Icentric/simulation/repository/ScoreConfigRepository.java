package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.ScoreConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoreConfigRepository extends JpaRepository<ScoreConfig, UUID> {
    Optional<ScoreConfig> findBySimulationId(UUID simulationId);
}

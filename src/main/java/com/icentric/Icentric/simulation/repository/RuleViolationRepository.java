package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.RuleViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleViolationRepository extends JpaRepository<RuleViolation, UUID> {
    List<RuleViolation> findByAttemptId(UUID attemptId);
    List<RuleViolation> findBySimulationId(UUID simulationId);
}

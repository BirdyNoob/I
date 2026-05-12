package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.EvaluationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationRuleRepository extends JpaRepository<EvaluationRule, UUID> {
    List<EvaluationRule> findBySimulationIdOrderBySortOrderAsc(UUID simulationId);
}

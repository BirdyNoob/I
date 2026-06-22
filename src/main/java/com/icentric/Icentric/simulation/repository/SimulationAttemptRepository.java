package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.SimulationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationAttemptRepository extends JpaRepository<SimulationAttempt, UUID> {

    List<SimulationAttempt> findByUserIdOrderByCompletedAtDesc(UUID userId);

    boolean existsByUserIdAndSimId(UUID userId, String simId);

    Optional<SimulationAttempt> findByUserIdAndSimId(UUID userId, String simId);

    List<SimulationAttempt> findBySimIdOrderByCompletedAtDesc(String simId);

    List<SimulationAttempt> findByTenantSlugOrderByCompletedAtDesc(String tenantSlug);

    long countBySimId(String simId);

    long countBySimIdAndPassedTrue(String simId);

    @Query("SELECT AVG(s.percentage) FROM SimulationAttempt s WHERE s.simId = :simId")
    Double avgPercentageBySimId(String simId);

    @Query("SELECT AVG(s.percentage) FROM SimulationAttempt s WHERE s.tenantSlug = :tenantSlug")
    Double avgPercentageByTenantSlug(String tenantSlug);

    long countByTenantSlug(String tenantSlug);

    long countByTenantSlugAndPassedTrue(String tenantSlug);

    List<SimulationAttempt> findByTenantSlug(String tenantSlug);

    List<SimulationAttempt> findByTenantSlugAndPassedFalse(String tenantSlug);
}

package com.icentric.Icentric.simulation.repository;

import com.icentric.Icentric.simulation.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByTrackId(UUID trackId);
    List<Simulation> findByPublishedTrue();
}

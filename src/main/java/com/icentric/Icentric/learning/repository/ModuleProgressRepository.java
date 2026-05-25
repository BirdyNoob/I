package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.ModuleProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuleProgressRepository extends JpaRepository<ModuleProgress, UUID> {
    Optional<ModuleProgress> findByUserIdAndModuleId(UUID userId, UUID moduleId);
    List<ModuleProgress> findByUserId(UUID userId);
}

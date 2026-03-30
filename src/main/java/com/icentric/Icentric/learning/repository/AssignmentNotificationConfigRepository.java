package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssignmentNotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentNotificationConfigRepository extends JpaRepository<AssignmentNotificationConfig, UUID> {
    Optional<AssignmentNotificationConfig> findByTenantId(UUID tenantId);
}

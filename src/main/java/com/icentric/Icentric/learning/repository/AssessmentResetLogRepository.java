package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentResetLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentResetLogRepository extends JpaRepository<AssessmentResetLog, UUID> {
    List<AssessmentResetLog> findByUserIdAndAssessmentConfigId(UUID userId, String assessmentConfigId);
    List<AssessmentResetLog> findByUserIdIn(List<UUID> userIds);
}

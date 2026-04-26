package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, UUID> {
    List<AssessmentAttempt> findByUserId(UUID userId);
    List<AssessmentAttempt> findByUserIdAndAssessmentConfigId(UUID userId, String assessmentConfigId);
    long countByUserIdAndAssessmentConfigId(UUID userId, String assessmentConfigId);
    List<AssessmentAttempt> findByUserIdAndStatus(UUID userId, String status);
}

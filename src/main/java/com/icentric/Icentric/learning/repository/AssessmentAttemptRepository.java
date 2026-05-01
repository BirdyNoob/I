package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, UUID> {

    List<AssessmentAttempt> findByUserId(UUID userId);
    List<AssessmentAttempt> findByUserIdAndAssessmentConfigId(UUID userId, String assessmentConfigId);
    long countByUserIdAndAssessmentConfigId(UUID userId, String assessmentConfigId);
    List<AssessmentAttempt> findByUserIdAndStatus(UUID userId, String status);

    // ── Analytics queries (replaces QuizAttemptRepository equivalents) ──────────

    /** Global average assessment score (0-100 scale). */
    @Query("SELECT AVG(a.score) FROM AssessmentAttempt a WHERE a.score IS NOT NULL")
    Double getAverageScore();

    /** Per-user average assessment score. */
    @Query("SELECT AVG(a.score) FROM AssessmentAttempt a WHERE a.userId = :userId AND a.score IS NOT NULL")
    Double getAverageScoreByUser(UUID userId);

    /** Average score within a time window. */
    @Query("SELECT AVG(a.score) FROM AssessmentAttempt a WHERE a.dateCompleted >= :from AND a.dateCompleted < :to AND a.score IS NOT NULL")
    Double getAverageScoreBetween(Instant from, Instant to);

    /**
     * Returns [assessmentConfigId, avgScore (0-100), attemptCount] per assessment.
     * Replaces QuizAttemptRepository.getLessonStats().
     */
    @Query("""
        SELECT a.assessmentConfigId,
               AVG(a.score),
               COUNT(a)
        FROM AssessmentAttempt a
        WHERE a.score IS NOT NULL
        GROUP BY a.assessmentConfigId
        """)
    List<Object[]> getAssessmentStats();

    /**
     * Returns [department, avgScore (0-100), passRate (0-1)] per department.
     * Replaces QuizAttemptRepository.getQuizPerformanceByDepartment().
     */
    @Query("""
        SELECT COALESCE(tu.department, 'UNKNOWN'),
               AVG(a.score),
               AVG(CASE WHEN a.status = 'PASSED' THEN 1.0 ELSE 0.0 END)
        FROM AssessmentAttempt a
        JOIN TenantUser tu ON tu.userId = a.userId
        WHERE tu.tenantId = :tenantId
        AND a.score IS NOT NULL
        GROUP BY COALESCE(tu.department, 'UNKNOWN')
        """)
    List<Object[]> getAssessmentPerformanceByDepartment(UUID tenantId);

    /**
     * Returns [assessmentConfigId, department, failureRate%] ordered by highest failure first.
     * Replaces QuizAttemptRepository.getLessonFailureRateByDepartment().
     */
    @Query("""
        SELECT a.assessmentConfigId,
               COALESCE(tu.department, 'UNKNOWN'),
               (SUM(CASE WHEN a.status = 'FAILED' THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(a)
        FROM AssessmentAttempt a
        JOIN TenantUser tu ON tu.userId = a.userId
        WHERE tu.tenantId = :tenantId
        GROUP BY a.assessmentConfigId, COALESCE(tu.department, 'UNKNOWN')
        ORDER BY (SUM(CASE WHEN a.status = 'FAILED' THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(a) DESC
        """)
    List<Object[]> getAssessmentFailureRateByDepartment(UUID tenantId);
}

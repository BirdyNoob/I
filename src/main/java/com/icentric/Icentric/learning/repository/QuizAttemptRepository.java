package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository
        extends JpaRepository<QuizAttempt, UUID> {
    @Query("""
SELECT AVG(CAST(q.score AS double) / q.totalQuestions)
FROM QuizAttempt q
""")
    Double getAverageScore();
    @Query("""
SELECT AVG(CAST(q.score AS double) / q.totalQuestions)
FROM QuizAttempt q
WHERE q.userId = :userId
""")
    Double getAverageScoreByUser(UUID userId);

    @Query("""
SELECT AVG(CAST(q.score AS double) / q.totalQuestions)
FROM QuizAttempt q
WHERE q.attemptedAt >= :from AND q.attemptedAt < :to
""")
    Double getAverageScoreBetween(java.time.Instant from, java.time.Instant to);
    @Query("""
SELECT q.lessonId, 
       AVG(CAST(q.score AS double) / q.totalQuestions), 
       COUNT(q)
FROM QuizAttempt q
GROUP BY q.lessonId
""")
    List<Object[]> getLessonStats();

    @Query("""
SELECT COALESCE(tu.department, 'UNKNOWN'),
       AVG(CAST(q.score AS double) / q.totalQuestions),
       AVG(CASE WHEN q.passed = true THEN 1.0 ELSE 0.0 END)
FROM QuizAttempt q
JOIN TenantUser tu ON tu.userId = q.userId
WHERE tu.tenantId = :tenantId
GROUP BY COALESCE(tu.department, 'UNKNOWN')
""")
    List<Object[]> getQuizPerformanceByDepartment(UUID tenantId);

    @Query("""
SELECT l.title,
       COALESCE(tu.department, 'UNKNOWN'),
       (SUM(CASE WHEN q.passed = false THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(q)
FROM QuizAttempt q
JOIN Lesson l ON l.id = q.lessonId
JOIN TenantUser tu ON tu.userId = q.userId
WHERE tu.tenantId = :tenantId
GROUP BY l.title, COALESCE(tu.department, 'UNKNOWN')
ORDER BY (SUM(CASE WHEN q.passed = false THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(q) DESC
""")
    List<Object[]> getLessonFailureRateByDepartment(UUID tenantId);

    long countByUserIdAndLessonId(UUID userId, UUID lessonId);
    long countByAttemptedAtAfter(java.time.Instant attemptedAt);
}

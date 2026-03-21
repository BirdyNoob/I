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
SELECT q.lessonId, 
       AVG(CAST(q.score AS double) / q.totalQuestions), 
       COUNT(q)
FROM QuizAttempt q
GROUP BY q.lessonId
""")
    List<Object[]> getLessonStats();
    long countByUserIdAndLessonId(UUID userId, UUID lessonId);
}

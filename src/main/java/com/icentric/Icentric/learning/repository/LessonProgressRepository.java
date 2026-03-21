package com.icentric.Icentric.learning.repository;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.learning.entity.LessonProgress;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository
        extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByUserIdAndLessonId(
            UUID userId,
            UUID lessonId
    );
    long countByUserIdAndStatus(UUID userId, String status);

    long countByUserId(UUID userId);
    boolean existsByUserIdAndLessonIdAndStatus(
            UUID userId,
            UUID lessonId,
            String status
    );
    @Query("""
SELECT COUNT(lp)
FROM LessonProgress lp
JOIN Lesson l ON lp.lessonId = l.id
JOIN CourseModule m ON l.moduleId = m.id
WHERE lp.userId = :userId
AND m.trackId = :trackId
AND lp.status = 'COMPLETED'
""")
    long countCompletedLessons(UUID userId, UUID trackId);
    @Query("""
SELECT COUNT(lp)
FROM LessonProgress lp
WHERE lp.userId = :userId
AND lp.status = 'COMPLETED'
""")
    long countCompletedByUser(UUID userId);
    @Modifying
    @Transactional
    @Query("""
DELETE FROM LessonProgress lp
WHERE lp.userId = :userId
AND lp.lessonId IN (
    SELECT l.id FROM Lesson l
    WHERE l.moduleId IN (
        SELECT m.id FROM CourseModule m
        WHERE m.trackId = :trackId
    )
)
""")
    void deleteByUserIdAndTrackId(UUID userId, UUID trackId);

}

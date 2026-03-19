package com.icentric.Icentric.learning.repository;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.learning.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
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

}

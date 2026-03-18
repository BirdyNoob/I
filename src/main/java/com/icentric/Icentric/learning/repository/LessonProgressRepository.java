package com.icentric.Icentric.learning.repository;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.learning.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

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

}

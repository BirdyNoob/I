package com.icentric.Icentric.content.repository;

import com.icentric.Icentric.content.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface LessonRepository
        extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByModuleId(UUID moduleId);
    long countByModuleId(UUID moduleId);
    List<Lesson> findByModuleIdOrderBySortOrder(UUID moduleId);
@Query("""
SELECT COUNT(l)
FROM Lesson l
JOIN CourseModule m ON l.moduleId = m.id
WHERE m.trackId = :trackId
""")
    long countLessonsInTrack(UUID trackId);

}

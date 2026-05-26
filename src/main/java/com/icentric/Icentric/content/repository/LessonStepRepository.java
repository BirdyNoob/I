package com.icentric.Icentric.content.repository;

import com.icentric.Icentric.content.entity.LessonStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonStepRepository extends JpaRepository<LessonStep, UUID> {
    List<LessonStep> findByLessonIdOrderBySortOrderAsc(UUID lessonId);
    List<LessonStep> findByLessonIdIn(java.util.Collection<UUID> lessonIds);
    void deleteByLessonId(UUID lessonId);
}

package com.icentric.Icentric.content.service;

import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.dto.LessonDetailResponse;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository repository;

    public LessonService(LessonRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a lesson inside a module. The lessonType must be one of the four
     * curriculum steps: VIDEO_CONCEPT, INTERACTIVE_SCENARIO, DOS_AND_DONTS, QUIZ.
     * Uniqueness per type within a module is enforced by the DB unique constraint
     * on (module_id, lesson_type). sortOrder drives the sequential lock logic.
     */
    @Transactional
    public LessonDetailResponse createLesson(UUID moduleId, CreateLessonRequest request) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModuleId(moduleId);
        lesson.setTitle(request.title());
        lesson.setLessonType(request.lessonType());
        lesson.setContentJson(request.contentJson());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setResourceUrl(request.resourceUrl());
        lesson.setSortOrder(request.sortOrder());
        lesson.setIsPublished(false);
        lesson.setCreatedAt(Instant.now());

        Lesson saved = repository.save(lesson);
        return toDetailResponse(saved);
    }

    /**
     * Returns full lesson detail for a learner to consume (content, video URL, etc.)
     */
    @Transactional(readOnly = true)
    public LessonDetailResponse getLesson(UUID lessonId) {
        Lesson lesson = repository.findById(lessonId)
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + lessonId));
        return toDetailResponse(lesson);
    }

    /**
     * Updates mutable fields (title, content, URLs). Cannot change lessonType or sortOrder
     * after creation — doing so would corrupt the sequential lock ordering.
     */
    @Transactional
    public LessonDetailResponse updateLesson(UUID lessonId, CreateLessonRequest request) {
        Lesson lesson = repository.findById(lessonId)
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + lessonId));

        if (request.title() != null)       lesson.setTitle(request.title());
        if (request.contentJson() != null) lesson.setContentJson(request.contentJson());
        if (request.videoUrl() != null)    lesson.setVideoUrl(request.videoUrl());
        if (request.resourceUrl() != null) lesson.setResourceUrl(request.resourceUrl());

        return toDetailResponse(repository.save(lesson));
    }

    /**
     * Soft-publishes a lesson (makes it visible to learners).
     */
    @Transactional
    public LessonDetailResponse publishLesson(UUID lessonId) {
        Lesson lesson = repository.findById(lessonId)
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + lessonId));
        lesson.setIsPublished(true);
        return toDetailResponse(repository.save(lesson));
    }

    // ── Mapper ─────────────────────────────────────────────────────────────────

    private LessonDetailResponse toDetailResponse(Lesson lesson) {
        return new LessonDetailResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getLessonType(),
                lesson.getContentJson(),
                lesson.getVideoUrl(),
                lesson.getResourceUrl()
        );
    }
}

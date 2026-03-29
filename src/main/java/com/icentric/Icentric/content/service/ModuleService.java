package com.icentric.Icentric.content.service;

import com.icentric.Icentric.content.dto.CreateModuleRequest;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ModuleService {

    private final ModuleRepository repository;
    private final LessonRepository lessonRepository;

    public ModuleService(ModuleRepository repository, LessonRepository lessonRepository) {
        this.repository = repository;
        this.lessonRepository = lessonRepository;
    }

    /**
     * Creates a module within a track. The caller is responsible for creating the
     * four lesson types (VIDEO_CONCEPT → INTERACTIVE_SCENARIO → DOS_AND_DONTS → QUIZ)
     * inside this module before publishing the parent track.
     */
    @Transactional
    public CourseModule createModule(UUID trackId, CreateModuleRequest request) {
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setTrackId(trackId);
        module.setTitle(request.title());
        module.setSortOrder(request.sortOrder());
        module.setIsPublished(false);
        module.setCreatedAt(Instant.now());
        return repository.save(module);
    }

    /**
     * Updates the title or sortOrder of a module.
     * Re-ordering sortOrder does NOT affect the sequential lock of its lessons —
     * lesson-level sequential locking is driven by lesson.sortOrder, not module.sortOrder.
     */
    @Transactional
    public CourseModule updateModule(UUID moduleId, CreateModuleRequest request) {
        CourseModule module = repository.findById(moduleId)
                .orElseThrow(() -> new NoSuchElementException("Module not found: " + moduleId));

        if (request.title() != null)     module.setTitle(request.title());
        if (request.sortOrder() != null) module.setSortOrder(request.sortOrder());

        return repository.save(module);
    }

    /**
     * Deletes a module and all its lessons.
     * Only allowed if the parent track is still in DRAFT status.
     */
    @Transactional
    public void deleteModule(UUID moduleId) {
        CourseModule module = repository.findById(moduleId)
                .orElseThrow(() -> new NoSuchElementException("Module not found: " + moduleId));

        // Delete all child lessons first (cascade not mapped at JPA level)
        List<Lesson> lessons = lessonRepository.findByModuleId(module.getId());
        if (!lessons.isEmpty()) {
            lessonRepository.deleteAllInBatch(lessons);
        }

        repository.deleteById(moduleId);
    }
}

package com.icentric.Icentric.content.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.dto.CreateModuleRequest;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.TrackRepository;
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
    private final TrackRepository trackRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public ModuleService(
            ModuleRepository repository,
            LessonRepository lessonRepository,
            TrackRepository trackRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.repository = repository;
        this.lessonRepository = lessonRepository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    /**
     * Creates a module within a track. The caller is responsible for creating the
     * four lesson types (VIDEO_CONCEPT → INTERACTIVE_SCENARIO → DOS_AND_DONTS → QUIZ)
     * inside this module before publishing the parent track.
     */
    @Transactional
    public CourseModule createModule(UUID trackId, CreateModuleRequest request) {
        assertTrackEditable(trackId);
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setTrackId(trackId);
        module.setTitle(request.title());
        module.setSortOrder(request.sortOrder());
        module.setIsPublished(false);
        module.setCreatedAt(Instant.now());
        CourseModule saved = repository.save(module);
        logModuleAction(AuditAction.CREATE_MODULE, saved, "created module");
        return saved;
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
        assertTrackEditable(module.getTrackId());

        if (request.title() != null)     module.setTitle(request.title());
        if (request.sortOrder() != null) module.setSortOrder(request.sortOrder());

        CourseModule saved = repository.save(module);
        logModuleAction(AuditAction.UPDATE_MODULE, saved, "updated module");
        return saved;
    }

    /**
     * Deletes a module and all its lessons.
     * Only allowed if the parent track is still in DRAFT status.
     */
    @Transactional
    public void deleteModule(UUID moduleId) {
        CourseModule module = repository.findById(moduleId)
                .orElseThrow(() -> new NoSuchElementException("Module not found: " + moduleId));
        assertTrackEditable(module.getTrackId());

        // Delete all child lessons first (cascade not mapped at JPA level)
        List<Lesson> lessons = lessonRepository.findByModuleId(module.getId());
        if (!lessons.isEmpty()) {
            lessonRepository.deleteAllInBatch(lessons);
        }

        repository.deleteById(moduleId);
        logModuleAction(AuditAction.DELETE_MODULE, module, "deleted module");
    }

    private void logModuleAction(AuditAction action, CourseModule module, String verb) {
        UUID actorId = currentActorUserId();
        if (actorId == null) {
            return;
        }
        auditService.log(
                actorId,
                action,
                "MODULE",
                module.getId().toString(),
                auditMetadataService.describeUser(actorId)
                        + " " + verb + " '" + module.getTitle() + "' [" + module.getId() + "]"
                        + " under " + auditMetadataService.describeTrack(module.getTrackId())
        );
    }

    private UUID currentActorUserId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        return userIdRaw == null ? null : UUID.fromString(userIdRaw.toString());
    }

    private void assertTrackEditable(UUID trackId) {
        var track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));
        if ("PUBLISHED".equals(track.getStatus())) {
            throw new IllegalStateException("Cannot modify a published track version. Create a new version instead.");
        }
    }
}

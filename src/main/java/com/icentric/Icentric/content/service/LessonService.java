package com.icentric.Icentric.content.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.dto.LessonDetailResponse;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository repository;
    private final ModuleRepository moduleRepository;
    private final TrackRepository trackRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public LessonService(
            LessonRepository repository,
            ModuleRepository moduleRepository,
            TrackRepository trackRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    /**
     * Creates a lesson inside a module. The lessonType must be one of the four
     * curriculum steps: VIDEO_CONCEPT, INTERACTIVE_SCENARIO, DOS_AND_DONTS, QUIZ.
     * Uniqueness per type within a module is enforced by the DB unique constraint
     * on (module_id, lesson_type). sortOrder drives the sequential lock logic.
     */
    @Transactional
    public LessonDetailResponse createLesson(UUID moduleId, CreateLessonRequest request) {
        assertTrackEditableByModule(moduleId);
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
        logLessonAction(AuditAction.CREATE_LESSON, saved, "created lesson");
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
        assertTrackEditableByLesson(lesson);

        if (request.title() != null)       lesson.setTitle(request.title());
        if (request.contentJson() != null) lesson.setContentJson(request.contentJson());
        if (request.videoUrl() != null)    lesson.setVideoUrl(request.videoUrl());
        if (request.resourceUrl() != null) lesson.setResourceUrl(request.resourceUrl());

        Lesson saved = repository.save(lesson);
        logLessonAction(AuditAction.UPDATE_LESSON, saved, "updated lesson");
        return toDetailResponse(saved);
    }

    /**
     * Soft-publishes a lesson (makes it visible to learners).
     */
    @Transactional
    public LessonDetailResponse publishLesson(UUID lessonId) {
        Lesson lesson = repository.findById(lessonId)
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + lessonId));
        assertTrackEditableByLesson(lesson);
        lesson.setIsPublished(true);
        Lesson saved = repository.save(lesson);
        logLessonAction(AuditAction.PUBLISH_LESSON, saved, "published lesson");
        return toDetailResponse(saved);
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

    private void logLessonAction(AuditAction action, Lesson lesson, String verb) {
        UUID actorId = currentActorUserId();
        if (actorId == null) {
            return;
        }
        CourseModule module = moduleRepository.findById(lesson.getModuleId()).orElse(null);
        String moduleLabel = module != null
                ? "'" + module.getTitle() + "' [" + module.getId() + "]"
                : "module " + lesson.getModuleId();
        String trackLabel = module != null
                ? auditMetadataService.describeTrack(module.getTrackId())
                : "unknown track";

        auditService.log(
                actorId,
                action,
                "LESSON",
                lesson.getId().toString(),
                auditMetadataService.describeUser(actorId)
                        + " " + verb + " '" + lesson.getTitle() + "' [" + lesson.getId() + "]"
                        + " in " + moduleLabel
                        + " for " + trackLabel
        );
    }

    private UUID currentActorUserId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        return userIdRaw == null ? null : UUID.fromString(userIdRaw.toString());
    }

    private void assertTrackEditableByLesson(Lesson lesson) {
        assertTrackEditableByModule(lesson.getModuleId());
    }

    private void assertTrackEditableByModule(UUID moduleId) {
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NoSuchElementException("Module not found: " + moduleId));
        Track track = trackRepository.findById(module.getTrackId())
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + module.getTrackId()));
        if ("PUBLISHED".equals(track.getStatus())) {
            throw new IllegalStateException("Cannot modify a published track version. Create a new version instead.");
        }
    }
}

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
import com.icentric.Icentric.content.repository.LessonStepRepository;
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
    private final LessonStepRepository lessonStepRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public LessonService(
            LessonRepository repository,
            ModuleRepository moduleRepository,
            TrackRepository trackRepository,
            LessonStepRepository lessonStepRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.trackRepository = trackRepository;
        this.lessonStepRepository = lessonStepRepository;
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
        lesson.setEstimatedMins(request.estimatedMins());
        lesson.setSortOrder(request.sortOrder());
        lesson.setIsPublished(false);
        lesson.setCreatedAt(Instant.now());

        Lesson saved = repository.save(lesson);

        if (request.steps() != null) {
            for (com.icentric.Icentric.content.dto.CreateLessonStepRequest stepReq : request.steps()) {
                com.icentric.Icentric.content.entity.LessonStep step = new com.icentric.Icentric.content.entity.LessonStep();
                step.setId(UUID.randomUUID());
                step.setLessonId(saved.getId());
                step.setStepType(com.icentric.Icentric.content.constants.StepType.valueOf(stepReq.stepType()));
                step.setTitle(stepReq.title());
                step.setContentJson(stepReq.contentJson());
                step.setSortOrder(stepReq.sortOrder());
                step.setCreatedAt(Instant.now());
                lessonStepRepository.save(step);
            }
        }

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
        if (request.estimatedMins() != null) lesson.setEstimatedMins(request.estimatedMins());

        Lesson saved = repository.save(lesson);

        if (request.steps() != null) {
            lessonStepRepository.deleteByLessonId(saved.getId());
            for (com.icentric.Icentric.content.dto.CreateLessonStepRequest stepReq : request.steps()) {
                com.icentric.Icentric.content.entity.LessonStep step = new com.icentric.Icentric.content.entity.LessonStep();
                step.setId(UUID.randomUUID());
                step.setLessonId(saved.getId());
                step.setStepType(com.icentric.Icentric.content.constants.StepType.valueOf(stepReq.stepType()));
                step.setTitle(stepReq.title());
                step.setContentJson(stepReq.contentJson());
                step.setSortOrder(stepReq.sortOrder());
                step.setCreatedAt(Instant.now());
                lessonStepRepository.save(step);
            }
        }

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

    /**
     * Fetches a specific step within a lesson.
     */
    @Transactional(readOnly = true)
    public com.icentric.Icentric.content.dto.LessonStepResponse getLessonStep(UUID lessonId, UUID stepId) {
        return lessonStepRepository.findById(stepId)
            .filter(step -> step.getLessonId().equals(lessonId))
            .map(step -> new com.icentric.Icentric.content.dto.LessonStepResponse(
                    step.getId(),
                    step.getLessonId(),
                    step.getStepType().name(),
                    step.getTitle(),
                    step.getContentJson(),
                    step.getSortOrder()
            ))
            .orElseThrow(() -> new NoSuchElementException("Step not found or does not belong to lesson"));
    }

    // ── Mapper ─────────────────────────────────────────────────────────────────

    private LessonDetailResponse toDetailResponse(Lesson lesson) {
        CourseModule module = moduleRepository.findById(lesson.getModuleId()).orElse(null);
        String moduleTitle = module != null ? module.getTitle() : "";
        var steps = lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lesson.getId());
        
        java.util.List<LessonDetailResponse.OutlineItem> outline = steps.stream().map(step ->
            new LessonDetailResponse.OutlineItem(
                step.getId(),
                step.getStepType().name(),
                step.getTitle(),
                "0:00", // Computed or fetched from payload
                false // Dynamic based on progress
            )
        ).toList();

        return new LessonDetailResponse(
                lesson.getId(),
                moduleTitle,
                lesson.getTitle(),
                lesson.getEstimatedMins(),
                steps.size(),
                steps.isEmpty() ? null : steps.get(0).getId(),
                outline
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

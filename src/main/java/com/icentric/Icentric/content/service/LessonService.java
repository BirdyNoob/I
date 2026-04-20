package com.icentric.Icentric.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository repository;
    private final ModuleRepository moduleRepository;
    private final TrackRepository trackRepository;
    private final LessonStepRepository lessonStepRepository;
    private final LessonProgressRepository progressRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;
    private final ObjectMapper objectMapper;
    private final TenantSchemaService tenantSchemaService;

    public LessonService(
            LessonRepository repository,
            ModuleRepository moduleRepository,
            TrackRepository trackRepository,
            LessonStepRepository lessonStepRepository,
            LessonProgressRepository progressRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService,
            ObjectMapper objectMapper,
            TenantSchemaService tenantSchemaService
    ) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.trackRepository = trackRepository;
        this.lessonStepRepository = lessonStepRepository;
        this.progressRepository = progressRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
        this.objectMapper = objectMapper;
        this.tenantSchemaService = tenantSchemaService;
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
        return toDetailResponse(saved, null); // admin create — no learner context
    }

    /**
     * Returns full lesson detail for a learner to consume.
     * isCompleted in outline and steps is resolved against the calling learner's progress.
     *
     * @param lessonId the lesson to load
     * @param userId   the authenticated learner's UUID (null = guest/admin preview, isCompleted always false)
     */
    @Transactional(readOnly = true)
    public LessonDetailResponse getLesson(UUID lessonId, UUID userId) {
        if (userId != null) {
            tenantSchemaService.applyCurrentTenantSearchPath();
        }
        Lesson lesson = repository.findById(lessonId)
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + lessonId));
        return toDetailResponse(lesson, userId);
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
        return toDetailResponse(saved, null); // admin update — no learner context
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
        return toDetailResponse(saved, null); // admin publish — no learner context
    }

    /**
     * Fetches a specific step within a lesson.
     *
     * @param lessonId the parent lesson
     * @param stepId   the step to load
     * @param userId   the authenticated learner's UUID (null = isCompleted always false)
     */
    @Transactional(readOnly = true)
    public com.icentric.Icentric.content.dto.LessonStepResponse getLessonStep(UUID lessonId, UUID stepId, UUID userId) {
        if (userId != null) {
            tenantSchemaService.applyCurrentTenantSearchPath();
        }
        
        boolean stepDone = false;
        if (userId != null) {
            stepDone = progressRepository.findByUserIdAndLessonId(userId, lessonId)
                    .map(p -> p.getCompletedStepIds().contains(stepId))
                    .orElse(false);
        }

        boolean finalStepDone = stepDone;
        return lessonStepRepository.findById(stepId)
            .filter(step -> step.getLessonId().equals(lessonId))
            .map(step -> new com.icentric.Icentric.content.dto.LessonStepResponse(
                    step.getId(),
                    step.getLessonId(),
                    step.getStepType().name(),
                    step.getTitle(),
                    extractDuration(step.getContentJson()),
                    finalStepDone,
                    step.getSortOrder(),
                    parseContentJson(step.getContentJson())  // structured Object, not raw string
            ))
            .orElseThrow(() -> new NoSuchElementException("Step not found or does not belong to lesson"));
    }

    /**
     * Deserializes the stored contentJson blob into a Map so Jackson serializes it
     * as a proper nested JSON object in the response (not a double-encoded string).
     */
    @SuppressWarnings("unchecked")
    private Object parseContentJson(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(contentJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return contentJson; // Fallback — return as-is so nothing is lost
        }
    }


    // ── Mapper ─────────────────────────────────────────────────────────────────

    private LessonDetailResponse toDetailResponse(Lesson lesson, UUID userId) {
        CourseModule module = moduleRepository.findById(lesson.getModuleId()).orElse(null);
        String moduleTitle = module != null ? module.getTitle() : "";
        var steps = lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lesson.getId());

        // Resolve progress tracking at the step level
        java.util.List<UUID> completedStepIds = new java.util.ArrayList<>();
        boolean lessonDone = false;
        if (userId != null) {
            progressRepository.findByUserIdAndLessonId(userId, lesson.getId())
                    .ifPresent(p -> {
                        if (p.getCompletedStepIds() != null) {
                            completedStepIds.addAll(p.getCompletedStepIds());
                        }
                    });
            lessonDone = progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lesson.getId(), "COMPLETED");
        }

        // ── Outline (sidebar) — lightweight, NO content payloads embedded ──────
        // Frontend renders the sidebar from this. Each step's content is loaded
        // on demand via GET /lessons/{lessonId}/steps/{stepId}.
        java.util.List<LessonDetailResponse.OutlineItem> outline = steps.stream().map(step ->
            new LessonDetailResponse.OutlineItem(
                step.getId(),
                step.getStepType().name(),
                step.getTitle(),
                extractDuration(step.getContentJson()),
                completedStepIds.contains(step.getId())
            )
        ).toList();

        // currentStepId = first incomplete step so the frontend knows which to load first;
        // if the lesson is fully done, point to the last step.
        UUID currentStepId = null;
        if (!steps.isEmpty()) {
            if (lessonDone || completedStepIds.size() >= steps.size()) {
                currentStepId = steps.get(steps.size() - 1).getId();
            } else {
                for (com.icentric.Icentric.content.entity.LessonStep step : steps) {
                    if (!completedStepIds.contains(step.getId())) {
                        currentStepId = step.getId();
                        break;
                    }
                }
                // Fallback (should never be reached if counts are correct)
                if (currentStepId == null) currentStepId = steps.get(steps.size() - 1).getId();
            }
        }

        Integer estimatedTimeLeftMinutes = computeEstimatedTimeLeftMinutes(
                lesson,
                steps,
                completedStepIds,
                lessonDone
        );

        return new LessonDetailResponse(
                lesson.getId(),
                moduleTitle,
                lesson.getTitle(),
                estimatedTimeLeftMinutes,
                steps.size(),
                currentStepId,
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

    /**
     * Reads the optional `durationFormatted` key from a stored contentJson blob.
     * Falls back to "0:00" if the key is absent or the JSON is malformed.
     */
    private String extractDuration(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) return "0:00";
        try {
            // Lightweight extraction — avoids pulling in a full ObjectMapper dependency
            int idx = contentJson.indexOf("\"durationFormatted\"");
            if (idx < 0) return "0:00";
            int colon = contentJson.indexOf(':', idx);
            int open  = contentJson.indexOf('"', colon);
            int close = contentJson.indexOf('"', open + 1);
            return contentJson.substring(open + 1, close);
        } catch (Exception e) {
            return "0:00";
        }
    }

    private Integer computeEstimatedTimeLeftMinutes(
            Lesson lesson,
            java.util.List<com.icentric.Icentric.content.entity.LessonStep> steps,
            java.util.List<UUID> completedStepIds,
            boolean lessonDone
    ) {
        if (lessonDone) {
            return 0;
        }
        if (steps.isEmpty()) {
            return lesson.getEstimatedMins();
        }
        if (completedStepIds.size() >= steps.size()) {
            return 0;
        }

        long remainingSeconds = 0;
        for (com.icentric.Icentric.content.entity.LessonStep step : steps) {
            if (completedStepIds.contains(step.getId())) {
                continue;
            }
            remainingSeconds += durationToSeconds(extractDuration(step.getContentJson()));
        }

        if (remainingSeconds > 0) {
            return (int) Math.ceil(remainingSeconds / 60.0);
        }
        return lesson.getEstimatedMins();
    }

    private long durationToSeconds(String durationFormatted) {
        if (durationFormatted == null || durationFormatted.isBlank()) {
            return 0;
        }

        String[] parts = durationFormatted.trim().split(":");
        try {
            if (parts.length == 2) {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1]);
                return Math.max(0, (minutes * 60) + seconds);
            }
            if (parts.length == 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                long seconds = Long.parseLong(parts[2]);
                return Math.max(0, (hours * 3600) + (minutes * 60) + seconds);
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return 0;
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

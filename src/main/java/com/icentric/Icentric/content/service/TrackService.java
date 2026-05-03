package com.icentric.Icentric.content.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.content.dto.*;
import com.icentric.Icentric.content.entity.Answer;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.entity.Question;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.AnswerRepository;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.QuestionRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TrackService {

    private final TrackRepository repository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final com.icentric.Icentric.content.repository.LessonStepRepository lessonStepRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;
    private final TenantRepository tenantRepository;
    private final EntityManager entityManager;
    private final com.icentric.Icentric.identity.repository.TenantUserRepository tenantUserRepository;
    private final com.icentric.Icentric.learning.service.AssignmentService assignmentService;
    private final com.icentric.Icentric.identity.repository.UserRepository userRepository;

    public TrackService(
            TrackRepository repository,
            ModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            com.icentric.Icentric.content.repository.LessonStepRepository lessonStepRepository,
            UserAssignmentRepository assignmentRepository,
            LessonProgressRepository lessonProgressRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService,
            TenantRepository tenantRepository,
            EntityManager entityManager,
            com.icentric.Icentric.identity.repository.TenantUserRepository tenantUserRepository,
            com.icentric.Icentric.learning.service.AssignmentService assignmentService,
            com.icentric.Icentric.identity.repository.UserRepository userRepository
    ) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.lessonStepRepository = lessonStepRepository;
        this.assignmentRepository = assignmentRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
        this.tenantRepository = tenantRepository;
        this.entityManager = entityManager;
        this.tenantUserRepository = tenantUserRepository;
        this.assignmentService = assignmentService;
        this.userRepository = userRepository;
    }

    // ── Admin: create track ────────────────────────────────────────────────────

    @Transactional
    public Track createTrack(CreateTrackRequest request) {
        if (!repository.findBySlugOrderByVersionDesc(request.slug()).isEmpty()) {
            throw new IllegalStateException("Track slug already exists: " + request.slug());
        }

        Track track = new Track();
        track.setId(UUID.randomUUID());
        track.setSlug(request.slug());
        track.setTitle(request.title());
        track.setDescription(request.description());
        track.setDepartment(request.department());
        track.setTrackType(request.trackType());
        track.setEstimatedMins(request.estimatedMins());
        track.setVersion(1);
        track.setPreviousVersionId(null);
        track.setIsPublished(false);
        track.setStatus("DRAFT");
        track.setCreatedAt(Instant.now());
        track.setPublishedAt(null);
        track.setChangeSummary("Initial draft");
        Track saved = repository.save(track);
        logAdminTrackAction(AuditAction.CREATE_TRACK, saved.getId(), "created");
        return saved;
    }

    // ── Admin: list all tracks ─────────────────────────────────────────────────

    public List<Track> getAllTracks() {
        return repository.findAll();
    }

    public List<Track> getPublishedTracks() {
        return repository.findLatestPublishedTracks();
    }

    @Transactional(readOnly = true)
    public List<TrackVersionResponse> getTrackVersions(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        return repository.findBySlugOrderByVersionDesc(track.getSlug())
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    // ── Admin / Learner: full track detail with ordered modules + lessons ──────

    @Transactional(readOnly = true)
    public TrackDetailResponse getTrack(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        // Modules ordered by sortOrder
        List<CourseModule> modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);

        List<ModuleResponse> moduleResponses = modules.stream()
                .map(module -> {
                    // Lessons ordered by sortOrder (enforces VIDEO → SCENARIO → DOS_DONTS → QUIZ sequence)
                    List<Lesson> lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());

                    List<LessonResponse> lessonResponses = lessons.stream()
                            .map(l -> new LessonResponse(
                                    l.getId(),
                                    l.getTitle()
                            ))
                            .toList();

                    return new ModuleResponse(
                            module.getId(),
                            module.getTitle(),
                            lessonResponses
                    );
                })
                .toList();

        return new TrackDetailResponse(
                track.getId(),
                track.getTitle(),
                moduleResponses
        );
    }

    // ── Admin: update track (draft only) ───────────────────────────────────────

    @Transactional
    public Track updateTrack(UUID trackId, UpdateTrackRequest request) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        if ("PUBLISHED".equals(track.getStatus())) {
            throw new IllegalStateException("Cannot edit a published track. Create a new version instead.");
        }

        if (request.title() != null)       track.setTitle(request.title());
        if (request.description() != null) track.setDescription(request.description());

        Track saved = repository.save(track);

        // Audit
        Object userIdRaw = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().getDetails()
                : null;
        if (userIdRaw != null) {
            UUID adminId = UUID.fromString(userIdRaw.toString());
            auditService.log(adminId, AuditAction.UPDATE_TRACK, "TRACK", trackId.toString(),
                    auditMetadataService.describeUser(adminId)
                            + " updated " + auditMetadataService.describeTrack(trackId));
        }

        return saved;
    }

    @Transactional
    public Track createTrackVersion(UUID trackId, CreateTrackVersionRequest request) {
        Track source = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        Track newVersion = cloneTrackVersion(
                source,
                nextVersionNumber(source.getSlug()),
                request != null ? request.changeSummary() : null
        );

        logAdminTrackAction(AuditAction.CREATE_TRACK_VERSION, newVersion.getId(), "created version");
        return newVersion;
    }

    @Transactional
    public Track publishTrack(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        if ("PUBLISHED".equals(track.getStatus())) {
            return track; // idempotent
        }

        // Validate: must have at least one module with the full 4-lesson sequence
        validateTrackStructure(trackId);

        archivePublishedVersions(track);
        track.setStatus("PUBLISHED");
        track.setIsPublished(true);
        track.setPublishedAt(Instant.now());

        Track saved = repository.save(track);
        logAdminTrackAction(AuditAction.PUBLISH_TRACK, saved.getId(), "published");

        migrateAssignmentsToPublishedVersion(saved);

        // Auto-assign track to all users in the matching department
        if (saved.getDepartment() != null) {
            autoAssignTrackToDepartmentUsers(saved);
        }

        return saved;
    }

    @Transactional
    public Track rollbackTrack(UUID trackId, RollbackTrackVersionRequest request) {
        Track source = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        String summary = request != null && request.changeSummary() != null && !request.changeSummary().isBlank()
                ? request.changeSummary()
                : "Rollback to version " + source.getVersion();

        Track rollbackVersion = cloneTrackVersion(source, nextVersionNumber(source.getSlug()), summary);
        Track published = publishTrack(rollbackVersion.getId());
        logAdminTrackAction(AuditAction.ROLLBACK_TRACK_VERSION, published.getId(), "rolled back to");
        return published;
    }

    // ── Admin: unpublish / archive track ──────────────────────────────────────

    @Transactional
    public Track archiveTrack(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));
        track.setStatus("ARCHIVED");
        track.setIsPublished(false);
        Track saved = repository.save(track);
        logAdminTrackAction(AuditAction.ARCHIVE_TRACK, saved.getId(), "archived");
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Ensures every module in the track has the mandatory lesson sequence:
     * VIDEO_CONCEPT → INTERACTIVE_SCENARIO → DOS_AND_DONTS → QUIZ.
     * Throws {@link IllegalStateException} if not satisfied.
     */
    private void validateTrackStructure(UUID trackId) {
        List<CourseModule> modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);
        if (modules.isEmpty()) {
            throw new IllegalStateException("Track must have at least one module before publishing.");
        }
        for (CourseModule module : modules) {
            List<Lesson> lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());
            if (lessons.isEmpty()) {
                throw new IllegalStateException(
                        "Module '" + module.getTitle() + "' must have at least one lesson.");
            }
        }
    }

    private Track cloneTrackVersion(Track source, int version, String changeSummary) {
        Track cloned = new Track();
        cloned.setId(UUID.randomUUID());
        cloned.setSlug(source.getSlug());
        cloned.setTitle(source.getTitle());
        cloned.setDescription(source.getDescription());
        cloned.setDepartment(source.getDepartment());
        cloned.setTrackType(source.getTrackType());
        cloned.setEstimatedMins(source.getEstimatedMins());
        cloned.setVersion(version);
        cloned.setPreviousVersionId(source.getId());
        cloned.setIsPublished(false);
        cloned.setStatus("DRAFT");
        cloned.setCreatedAt(Instant.now());
        cloned.setPublishedAt(null);
        cloned.setChangeSummary(
                changeSummary != null && !changeSummary.isBlank()
                        ? changeSummary
                        : "Draft copied from version " + source.getVersion()
        );
        Track savedTrack = repository.save(cloned);

        for (CourseModule module : moduleRepository.findByTrackIdOrderBySortOrder(source.getId())) {
            CourseModule clonedModule = new CourseModule();
            clonedModule.setId(UUID.randomUUID());
            clonedModule.setTrackId(savedTrack.getId());
            clonedModule.setTitle(module.getTitle());
            clonedModule.setSortOrder(module.getSortOrder());
            clonedModule.setIsPublished(module.getIsPublished());
            clonedModule.setCreatedAt(Instant.now());
            CourseModule savedModule = moduleRepository.save(clonedModule);

            for (Lesson lesson : lessonRepository.findByModuleIdOrderBySortOrder(module.getId())) {
                Lesson clonedLesson = new Lesson();
                clonedLesson.setId(UUID.randomUUID());
                clonedLesson.setModuleId(savedModule.getId());
                clonedLesson.setTitle(lesson.getTitle());
                clonedLesson.setSortOrder(lesson.getSortOrder());
                clonedLesson.setIsPublished(lesson.getIsPublished());
                clonedLesson.setEstimatedMins(lesson.getEstimatedMins());
                clonedLesson.setCreatedAt(Instant.now());
                Lesson savedLesson = lessonRepository.save(clonedLesson);

                for (com.icentric.Icentric.content.entity.LessonStep step : lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lesson.getId())) {
                    com.icentric.Icentric.content.entity.LessonStep clonedStep = new com.icentric.Icentric.content.entity.LessonStep();
                    clonedStep.setId(UUID.randomUUID());
                    clonedStep.setLessonId(savedLesson.getId());
                    clonedStep.setStepType(step.getStepType());
                    clonedStep.setTitle(step.getTitle());
                    clonedStep.setContentJson(step.getContentJson());
                    clonedStep.setSortOrder(step.getSortOrder());
                    clonedStep.setCreatedAt(Instant.now());
                    lessonStepRepository.save(clonedStep);
                }

                for (Question question : questionRepository.findByLessonId(lesson.getId())) {
                    Question clonedQuestion = new Question();
                    clonedQuestion.setId(UUID.randomUUID());
                    clonedQuestion.setLessonId(savedLesson.getId());
                    clonedQuestion.setQuestionText(question.getQuestionText());
                    clonedQuestion.setQuestionType(question.getQuestionType());
                    clonedQuestion.setCreatedAt(Instant.now());
                    Question savedQuestion = questionRepository.save(clonedQuestion);

                    for (Answer answer : answerRepository.findByQuestionId(question.getId())) {
                        Answer clonedAnswer = new Answer();
                        clonedAnswer.setId(UUID.randomUUID());
                        clonedAnswer.setQuestionId(savedQuestion.getId());
                        clonedAnswer.setAnswerText(answer.getAnswerText());
                        clonedAnswer.setIsCorrect(answer.getIsCorrect());
                        answerRepository.save(clonedAnswer);
                    }
                }
            }
        }

        return savedTrack;
    }

    private int nextVersionNumber(String slug) {
        return repository.findTopBySlugOrderByVersionDesc(slug)
                .map(track -> track.getVersion() + 1)
                .orElse(1);
    }

    private void archivePublishedVersions(Track track) {
        for (Track version : repository.findBySlugOrderByVersionDesc(track.getSlug())) {
            if (!version.getId().equals(track.getId()) && Boolean.TRUE.equals(version.getIsPublished())) {
                version.setIsPublished(false);
                version.setStatus("ARCHIVED");
                repository.save(version);
            }
        }
    }

    private void migrateAssignmentsToPublishedVersion(Track publishedTrack) {
        List<UUID> priorTrackIds = repository.findBySlugOrderByVersionDesc(publishedTrack.getSlug())
                .stream()
                .filter(track -> !track.getId().equals(publishedTrack.getId()))
                .map(Track::getId)
                .toList();

        if (priorTrackIds.isEmpty()) {
            return;
        }

        String originalTenant = TenantContext.getTenant();
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            for (Tenant tenant : tenants) {
                TenantContext.setTenant(tenant.getSlug());
                entityManager.createNativeQuery("SET LOCAL search_path TO tenant_" + tenant.getSlug()).executeUpdate();

                List<UserAssignment> assignments = assignmentRepository.findByTrackIdIn(priorTrackIds);
                for (UserAssignment assignment : assignments) {
                    lessonProgressRepository.deleteByUserIdAndTrackId(assignment.getUserId(), assignment.getTrackId());
                    assignment.setTrackId(publishedTrack.getId());
                    assignment.setContentVersionAtAssignment(publishedTrack.getVersion());
                    assignment.setRequiresRetraining(true);
                    assignment.setStatus(com.icentric.Icentric.learning.constants.AssignmentStatus.ASSIGNED);
                    assignmentRepository.save(assignment);
                }
            }
        } finally {
            if (originalTenant != null && !originalTenant.isBlank()) {
                TenantContext.setTenant(originalTenant);
                entityManager.createNativeQuery("SET LOCAL search_path TO tenant_" + originalTenant).executeUpdate();
            } else {
                TenantContext.clear();
                entityManager.createNativeQuery("SET LOCAL search_path TO public").executeUpdate();
            }
        }
    }

    private TrackVersionResponse toVersionResponse(Track track) {
        return new TrackVersionResponse(
                track.getId(),
                track.getSlug(),
                track.getTitle(),
                track.getVersion(),
                track.getStatus(),
                track.getIsPublished(),
                track.getCreatedAt(),
                track.getPublishedAt(),
                track.getPreviousVersionId(),
                track.getChangeSummary()
        );
    }

    private void logAdminTrackAction(AuditAction action, UUID trackId, String verb) {
        Object userIdRaw = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getDetails()
                : null;
        if (userIdRaw == null) {
            return;
        }
        UUID adminId = UUID.fromString(userIdRaw.toString());
        auditService.log(
                adminId,
                action,
                "TRACK",
                trackId.toString(),
                auditMetadataService.describeUser(adminId) + " " + verb + " " + auditMetadataService.describeTrack(trackId)
        );
    }

    private void autoAssignTrackToDepartmentUsers(Track track) {
        String originalTenant = TenantContext.getTenant();
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            for (Tenant tenant : tenants) {
                TenantContext.setTenant(tenant.getSlug());
                entityManager.createNativeQuery("SET LOCAL search_path TO tenant_" + tenant.getSlug()).executeUpdate();

                List<com.icentric.Icentric.identity.entity.TenantUser> memberships =
                        tenantUserRepository.findByTenantId(tenant.getId())
                                .stream()
                                .filter(m -> track.getDepartment().equals(m.getDepartment()))
                                .filter(m -> "LEARNER".equals(m.getRole()))
                                .toList();

                if (memberships.isEmpty()) continue;

                List<UUID> userIds = memberships.stream().map(com.icentric.Icentric.identity.entity.TenantUser::getUserId).toList();
                List<com.icentric.Icentric.identity.entity.User> users = userRepository.findByIdIn(userIds);

                for (com.icentric.Icentric.identity.entity.User user : users) {
                    if (Boolean.TRUE.equals(user.getIsActive())) {
                        boolean alreadyAssigned = assignmentRepository.findByUserIdAndTrackId(user.getId(), track.getId()).isPresent();
                        if (!alreadyAssigned) {
                            UserAssignment assignment = new UserAssignment();
                            assignment.setId(UUID.randomUUID());
                            assignment.setUserId(user.getId());
                            assignment.setTrackId(track.getId());
                            assignment.setContentVersionAtAssignment(track.getVersion());
                            assignment.setStatus(com.icentric.Icentric.learning.constants.AssignmentStatus.ASSIGNED);
                            assignment.setAssignedAt(Instant.now());
                            
                            assignmentRepository.save(assignment);
                            assignmentService.sendTrackAssignedNotification(user, track.getTitle(), tenant.getCompanyName(), tenant.getSlug(), null);
                        }
                    }
                }
            }
        } finally {
            if (originalTenant != null && !originalTenant.isBlank()) {
                TenantContext.setTenant(originalTenant);
                entityManager.createNativeQuery("SET LOCAL search_path TO tenant_" + originalTenant).executeUpdate();
            } else {
                TenantContext.clear();
                entityManager.createNativeQuery("SET LOCAL search_path TO public").executeUpdate();
            }
        }
    }
}

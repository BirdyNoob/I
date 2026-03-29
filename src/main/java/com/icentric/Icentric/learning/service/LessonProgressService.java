package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.LessonProgressRequest;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.exception.SequentialLockException;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.service.TenantProvisioningService;
import com.icentric.Icentric.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LessonProgressService {

    private final LessonProgressRepository repository;
    private final EntityManager entityManager;
    private final TenantProvisioningService tenantProvisioningService;
    private final UserAssignmentRepository assignmentRepository;
    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public LessonProgressService(
            LessonProgressRepository repository,
            EntityManager entityManager,
            TenantProvisioningService tenantProvisioningService,
            UserAssignmentRepository assignmentRepository,
            LessonRepository lessonRepository,
            ModuleRepository moduleRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.tenantProvisioningService = tenantProvisioningService;
        this.assignmentRepository = assignmentRepository;
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    @Transactional
    public LessonProgress updateProgress(UUID userId, LessonProgressRequest request) {

        // ── 1. Tenant schema setup ────────────────────────────────────────────
        String tenant = TenantContext.getTenant();
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("Missing tenant in request context");
        }
        if ("system".equals(tenant)) {
            entityManager.createNativeQuery("SET LOCAL search_path TO system").executeUpdate();
        } else {
            if (!tenant.matches("[a-zA-Z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid tenant slug: " + tenant);
            }
            tenantProvisioningService.provisionTenantSchema(tenant);
            entityManager.createNativeQuery("SET LOCAL search_path TO tenant_" + tenant).executeUpdate();
        }

        // ── 2. Sequential lock guard ──────────────────────────────────────────
        // Load the target lesson and all siblings ordered by sortOrder.
        Lesson targetLesson = lessonRepository.findById(request.lessonId())
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + request.lessonId()));

        List<Lesson> moduleLessons = lessonRepository
                .findByModuleIdOrderBySortOrder(targetLesson.getModuleId());

        // Every lesson with a lower sortOrder must already be COMPLETED.
        for (Lesson sibling : moduleLessons) {
            if (sibling.getSortOrder() >= targetLesson.getSortOrder()) {
                break; // reached or passed the target — stop
            }
            boolean siblingCompleted = repository.existsByUserIdAndLessonIdAndStatus(
                    userId, sibling.getId(), "COMPLETED");
            if (!siblingCompleted) {
                auditService.log(
                        userId,
                        AuditAction.LESSON_ACCESS_BLOCKED,
                        "LESSON",
                        request.lessonId().toString(),
                        auditMetadataService.describeUserInCurrentTenant(userId)
                                + " attempted to access lesson " + request.lessonId()
                                + " in " + auditMetadataService.currentTenantLabel()
                                + " before completing prerequisite lesson " + sibling.getId()
                                + " (" + sibling.getTitle() + ")"
                );
                throw new SequentialLockException(sibling.getTitle());
            }
        }

        // ── 3. Upsert lesson progress ─────────────────────────────────────────
        final boolean[] created = {false};
        LessonProgress progress = repository
                .findByUserIdAndLessonId(userId, request.lessonId())
                .orElseGet(() -> {
                    created[0] = true;
                    LessonProgress p = new LessonProgress();
                    p.setId(UUID.randomUUID());
                    p.setUserId(userId);
                    p.setLessonId(request.lessonId());
                    p.setCreatedAt(Instant.now());
                    p.setRequiresRetraining(false);
                    return p;
                });

        progress.setStatus(request.status());
        if ("COMPLETED".equals(request.status())) {
            progress.setCompletedAt(Instant.now());
        }

        LessonProgress saved = repository.save(progress);

        // ── 4. Mark assignment IN_PROGRESS on first lesson touch ──────────────
        UUID trackId = moduleRepository.findById(targetLesson.getModuleId())
                .orElseThrow().getTrackId();
        markInProgress(userId, trackId, request.lessonId());

        if (created[0]) {
            auditService.log(
                    userId,
                    AuditAction.LESSON_STARTED,
                    "LESSON",
                    request.lessonId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(userId)
                            + " started lesson " + request.lessonId()
                            + " in " + auditMetadataService.describeTrack(trackId)
            );
        }

        if ("COMPLETED".equals(request.status())) {
            auditService.log(
                    userId,
                    AuditAction.LESSON_COMPLETED,
                    "LESSON",
                    request.lessonId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(userId)
                            + " completed lesson " + request.lessonId()
                            + " in " + auditMetadataService.describeTrack(trackId)
            );
        } else {
            auditService.log(
                    userId,
                    AuditAction.LESSON_PROGRESS_UPDATED,
                    "LESSON",
                    request.lessonId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(userId)
                            + " updated lesson " + request.lessonId()
                            + " status to " + request.status()
            );
        }

        // ── 5. Auto-complete track assignment when every lesson is finished ────
        if ("COMPLETED".equals(request.status())) {
            checkAndCompleteTrack(userId, trackId);
        }

        return saved;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    public void markInProgress(UUID userId, UUID trackId, UUID lessonId) {
        var assignment = assignmentRepository
                .findByUserIdAndTrackId(userId, trackId)
                .orElseThrow();
        if (AssignmentStatus.ASSIGNED.equals(assignment.getStatus())) {
            assignment.setStatus(AssignmentStatus.IN_PROGRESS);
            assignmentRepository.save(assignment);
            auditService.log(
                    userId,
                    AuditAction.COURSE_STARTED,
                    "ASSIGNMENT",
                    assignment.getId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(userId)
                            + " started " + auditMetadataService.describeTrack(trackId)
                            + " by opening lesson " + lessonId
            );
        }
    }

    private void checkAndCompleteTrack(UUID userId, UUID trackId) {
        long totalLessons = lessonRepository.countLessonsInTrack(trackId);
        long completedLessons = repository.countCompletedLessons(userId, trackId);
        if (totalLessons > 0 && completedLessons >= totalLessons) {
            var assignment = assignmentRepository
                    .findByUserIdAndTrackId(userId, trackId)
                    .orElseThrow();
            assignment.setStatus(AssignmentStatus.COMPLETED);
            assignmentRepository.save(assignment);
            auditService.log(
                    userId,
                    AuditAction.COURSE_COMPLETED,
                    "ASSIGNMENT",
                    assignment.getId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(userId)
                            + " completed " + auditMetadataService.describeTrack(trackId)
                            + " after finishing " + completedLessons + " lessons"
            );
        }
    }
}

package com.icentric.Icentric.learning.service;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.LessonProgressRequest;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.service.TenantProvisioningService;
import com.icentric.Icentric.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class LessonProgressService {

    private final LessonProgressRepository repository;
    private final EntityManager entityManager;
    private final TenantProvisioningService tenantProvisioningService;
    private final UserAssignmentRepository assignmentRepository;
    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;

    public LessonProgressService(
            LessonProgressRepository repository,
            EntityManager entityManager,
            TenantProvisioningService tenantProvisioningService,
            UserAssignmentRepository assignmentRepository,
            LessonRepository lessonRepository,
            ModuleRepository moduleRepository
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.tenantProvisioningService = tenantProvisioningService;
        this.assignmentRepository = assignmentRepository;
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
    }

    @Transactional
    public LessonProgress updateProgress(
            UUID userId,
            LessonProgressRequest request
    ) {
        // Ensure Hibernate uses the tenant schema on the same transaction/connection.
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
            // Backfill migrations for existing tenant schemas that were created before
            // lesson_progress migration existed.
            tenantProvisioningService.provisionTenantSchema(tenant);
            entityManager.createNativeQuery("SET LOCAL search_path TO tenant_" + tenant).executeUpdate();
        }

        LessonProgress progress =
                repository.findByUserIdAndLessonId(
                        userId,
                        request.lessonId()
                ).orElseGet(() -> {

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

// 🔥 Resolve trackId
        var lesson = lessonRepository.findById(request.lessonId())
                .orElseThrow();

        var module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow();

        UUID trackId = module.getTrackId();

// 🔥 Move assignment to IN_PROGRESS
        markInProgress(userId, trackId);

        return saved;

    }
    public void markInProgress(UUID userId, UUID trackId) {

        var assignment = assignmentRepository
                .findByUserIdAndTrackId(userId, trackId)
                .orElseThrow();

        if (AssignmentStatus.ASSIGNED.equals(assignment.getStatus())) {
            assignment.setStatus(AssignmentStatus.IN_PROGRESS);
            assignmentRepository.save(assignment);
        }
    }
}

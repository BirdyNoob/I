package com.icentric.Icentric.learning.service;
import com.icentric.Icentric.learning.dto.LessonProgressRequest;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
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

    public LessonProgressService(
            LessonProgressRepository repository,
            EntityManager entityManager,
            TenantProvisioningService tenantProvisioningService
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.tenantProvisioningService = tenantProvisioningService;
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

        return repository.save(progress);
    }
}

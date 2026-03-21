package com.icentric.Icentric.jobs;

import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.service.NotificationService;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class AssignmentScheduler {

    private final UserAssignmentRepository assignmentRepository;
    private final TenantRepository tenantRepository;
    private final EntityManager entityManager;
    private final NotificationService notificationService;

    public AssignmentScheduler(
            UserAssignmentRepository assignmentRepository,
            TenantRepository tenantRepository,
            EntityManager entityManager,
            NotificationService notificationService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.tenantRepository = tenantRepository;
        this.entityManager = entityManager;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void markOverdueAssignments() {
        var tenants = tenantRepository.findAll();

        for (var tenant : tenants) {
            markOverdueAssignmentsForTenant(tenant.getSlug());
        }
    }

    public void markOverdueAssignmentsForTenant(String tenantSlug) {
        TenantContext.setTenant(tenantSlug);

        entityManager.createNativeQuery(
                "SET LOCAL search_path TO tenant_" + tenantSlug
        ).executeUpdate();

        try {
            List<UserAssignment> assignments = assignmentRepository.findAll();

            for (UserAssignment a : assignments) {

                if (a.getDueDate() != null &&
                        a.getDueDate().isBefore(Instant.now()) &&
                        !AssignmentStatus.COMPLETED.equals(a.getStatus())) {

                    a.setStatus(AssignmentStatus.OVERDUE);
                    assignmentRepository.save(a);
                    notificationService.createNotification(
                            a.getUserId(),
                            "OVERDUE",
                            "Your training is overdue. Please complete it."
                    );
                }
            }
        } finally {
            TenantContext.clear();
        }
    }
}

package com.icentric.Icentric.learning.scheduler;

import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.constants.NotificationType;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.NotificationRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.service.NotificationService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssignmentNotificationScheduler {
    private static final Duration REMINDER_COOLDOWN = Duration.ofHours(24);
    private static final Duration ESCALATION_COOLDOWN = Duration.ofHours(24);

    private final UserAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantSchemaService tenantSchemaService;

    public AssignmentNotificationScheduler(
            UserAssignmentRepository assignmentRepository,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            TenantSchemaService tenantSchemaService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Scheduled(fixedRate = 60000) // every 1 min (for testing)
    @Transactional
    public void processAssignments() {
        Instant now = Instant.now();

        for (var tenant : tenantRepository.findAll()) {
            processAssignmentsForTenant(tenant, now);
        }
    }

    private void processAssignmentsForTenant(Tenant tenant, Instant now) {
        TenantContext.setTenant(tenant.getSlug());

        try {
            tenantSchemaService.applyCurrentTenantSearchPath();
            processReminders(now);
            processEscalations(tenant, now);
        } finally {
            TenantContext.clear();
        }
    }

    private void processReminders(Instant now) {
        List<UserAssignment> assignments = assignmentRepository.findByStatusInAndDueDateIsNotNull(
                List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS)
        );

        for (UserAssignment assignment : assignments) {
            if (assignment.getDueDate() == null) {
                continue;
            }

            long hoursLeft = Duration.between(now, assignment.getDueDate()).toHours();

            if (hoursLeft > 0 && hoursLeft <= 48) {
                Instant cooldownStart = now.minus(REMINDER_COOLDOWN);
                if (!notificationRepository.existsByUserIdAndTypeAndCreatedAtAfter(
                        assignment.getUserId(),
                        NotificationType.REMINDER,
                        cooldownStart)) {

                    notificationService.createNotification(
                            assignment.getUserId(),
                            NotificationType.REMINDER,
                            "Training is due soon"
                    );
                }
            }
        }
    }

    private void processEscalations(Tenant tenant, Instant now) {
        List<UserAssignment> overdueAssignments = assignmentRepository.findByStatusAndDueDateIsNotNull(
                AssignmentStatus.OVERDUE
        );

        if (overdueAssignments.isEmpty()) {
            return;
        }

        // Find an admin within this tenant via the tenant_users junction table
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId());

        Optional<TenantUser> admin = memberships.stream()
                .filter(m -> "SUPER_ADMIN".equals(m.getRole()))
                .findFirst();

        if (admin.isEmpty()) {
            admin = memberships.stream()
                    .filter(m -> "ADMIN".equals(m.getRole()))
                    .findFirst();
        }

        if (admin.isEmpty()) {
            return;
        }

        UUID adminUserId = admin.get().getUserId();

        UserAssignment firstOverdue = overdueAssignments.get(0);
        String message = "User " + firstOverdue.getUserId() + " has overdue training";
        Instant cooldownStart = now.minus(ESCALATION_COOLDOWN);

        if (notificationRepository.existsByUserIdAndTypeAndMessageAndCreatedAtAfter(
                adminUserId,
                NotificationType.ESCALATION,
                message,
                cooldownStart)) {
            return;
        }

        notificationService.createNotification(
                adminUserId,
                NotificationType.ESCALATION,
                message
        );
    }
}


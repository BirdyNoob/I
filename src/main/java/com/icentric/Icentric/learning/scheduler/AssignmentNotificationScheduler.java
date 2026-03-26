package com.icentric.Icentric.learning.scheduler;

import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.NotificationRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.service.NotificationService;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AssignmentNotificationScheduler {

    private final UserAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantSchemaService tenantSchemaService;

    public AssignmentNotificationScheduler(
            UserAssignmentRepository assignmentRepository,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantSchemaService tenantSchemaService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Scheduled(fixedRate = 60000) // every 1 min (for testing)
    @Transactional
    public void processAssignments() {
        Instant now = Instant.now();

        for (var tenant : tenantRepository.findAll()) {
            processAssignmentsForTenant(tenant.getSlug(), now);
        }
    }

    private void processAssignmentsForTenant(String tenantSlug, Instant now) {
        TenantContext.setTenant(tenantSlug);

        try {
            tenantSchemaService.applyCurrentTenantSearchPath();
            processReminders(now);
            processEscalations();
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
                if (!notificationRepository.existsByUserIdAndTypeAndSentFalse(
                        assignment.getUserId(), "REMINDER")) {

                    notificationService.createNotification(
                            assignment.getUserId(),
                            "REMINDER",
                            "Training is due soon"
                    );
                }
            }
        }
    }

    private void processEscalations() {
        List<UserAssignment> overdueAssignments = assignmentRepository.findByStatusAndDueDateIsNotNull(
                AssignmentStatus.OVERDUE
        );

        if (overdueAssignments.isEmpty()) {
            return;
        }

        List<User> admins = userRepository.findByRole("SUPER_ADMIN", PageRequest.of(0, 1)).getContent();
        if (admins.isEmpty()) {
            admins = userRepository.findByRole("ADMIN", PageRequest.of(0, 1)).getContent();
        }
        if (admins.isEmpty()) {
            return;
        }

        UUID adminUserId = admins.get(0).getId();

        if (notificationRepository.existsByUserIdAndTypeAndSentFalse(
                adminUserId, "ESCALATION")) {
            return;
        }

        UserAssignment firstOverdue = overdueAssignments.get(0);
        notificationService.createNotification(
                adminUserId,
                "ESCALATION",
                "User " + firstOverdue.getUserId() + " has overdue training"
        );
    }
}

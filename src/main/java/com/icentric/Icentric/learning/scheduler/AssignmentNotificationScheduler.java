package com.icentric.Icentric.learning.scheduler;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
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
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    public AssignmentNotificationScheduler(
            UserAssignmentRepository assignmentRepository,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            TenantSchemaService tenantSchemaService,
            AuditService auditService,
            AuditMetadataService auditMetadataService,
            UserRepository userRepository,
            TrackRepository trackRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
        this.userRepository = userRepository;
        this.trackRepository = trackRepository;
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
                    auditService.log(
                            assignment.getUserId(),
                            AuditAction.ASSIGNMENT_REMINDER_SENT,
                            "ASSIGNMENT",
                            assignment.getId().toString(),
                            "Due-soon reminder sent to "
                                    + auditMetadataService.describeUserInCurrentTenant(assignment.getUserId())
                                    + " for "
                                    + auditMetadataService.describeTrack(assignment.getTrackId())
                                    + " with " + hoursLeft + " hours remaining"
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
        String message = buildAdminEscalationMessage(firstOverdue);
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
        auditService.log(
                adminUserId,
                AuditAction.ASSIGNMENT_ESCALATION_SENT,
                "ASSIGNMENT",
                firstOverdue.getId().toString(),
                "Escalation sent to "
                        + auditMetadataService.describeUserInCurrentTenant(adminUserId)
                        + " about overdue learner "
                        + auditMetadataService.describeUserInCurrentTenant(firstOverdue.getUserId())
                        + " on " + auditMetadataService.describeTrack(firstOverdue.getTrackId())
        );
    }

    private String buildAdminEscalationMessage(UserAssignment assignment) {
        User learner = userRepository.findById(assignment.getUserId()).orElse(null);
        Track track = trackRepository.findById(assignment.getTrackId()).orElse(null);

        String learnerName = learner != null && learner.getName() != null && !learner.getName().isBlank()
                ? learner.getName()
                : "Unknown user";
        String learnerEmail = learner != null && learner.getEmail() != null && !learner.getEmail().isBlank()
                ? " (" + learner.getEmail() + ")"
                : "";
        String trackTitle = track != null && track.getTitle() != null && !track.getTitle().isBlank()
                ? track.getTitle()
                : assignment.getTrackId().toString();
        String dueDate = assignment.getDueDate() != null ? assignment.getDueDate().toString() : "unknown due date";

        return "Overdue training alert: " + learnerName + learnerEmail
                + " has not completed '" + trackTitle + "' due on " + dueDate + ".";
    }
}

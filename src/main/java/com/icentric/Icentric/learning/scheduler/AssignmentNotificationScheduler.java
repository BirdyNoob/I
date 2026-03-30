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
import com.icentric.Icentric.learning.service.ReminderConfigService;
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
import java.util.UUID;

@Service
public class AssignmentNotificationScheduler {

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
    private final ReminderConfigService reminderConfigService;

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
            TrackRepository trackRepository,
            ReminderConfigService reminderConfigService
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
        this.reminderConfigService = reminderConfigService;
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
            ReminderConfigService.ReminderSettings settings = reminderConfigService.resolveConfig(tenant.getId());
            processReminders(now, settings);
            processEscalations(tenant, now, settings);
        } finally {
            TenantContext.clear();
        }
    }

    private void processReminders(Instant now, ReminderConfigService.ReminderSettings settings) {
        if (!settings.reminderEnabled()) {
            return;
        }

        List<UserAssignment> assignments = assignmentRepository.findByStatusInAndDueDateIsNotNull(
                List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS)
        );

        for (UserAssignment assignment : assignments) {
            if (assignment.getDueDate() == null) {
                continue;
            }

            long hoursLeft = Duration.between(now, assignment.getDueDate()).toHours();

            for (Integer offsetHours : settings.reminderOffsetsHours()) {
                if (hoursLeft > 0 && hoursLeft <= offsetHours) {
                    String eventKey = reminderEventKey(assignment.getId(), offsetHours);
                    if (!notificationRepository.existsByEventKey(eventKey)) {
                        String message = buildLearnerReminderMessage(assignment, offsetHours);
                    notificationService.createNotification(
                            assignment.getUserId(),
                            NotificationType.REMINDER,
                                message,
                                eventKey
                    );
                    auditService.log(
                            assignment.getUserId(),
                            AuditAction.ASSIGNMENT_REMINDER_SENT,
                            "ASSIGNMENT",
                            assignment.getId().toString(),
                                "Reminder (" + offsetHours + "h) sent to "
                                    + auditMetadataService.describeUserInCurrentTenant(assignment.getUserId())
                                    + " for "
                                    + auditMetadataService.describeTrack(assignment.getTrackId())
                                    + " with " + hoursLeft + " hours remaining"
                    );
                        break;
                    }
                }
            }
        }
    }

    private void processEscalations(Tenant tenant, Instant now, ReminderConfigService.ReminderSettings settings) {
        if (!settings.escalationEnabled()) {
            return;
        }

        List<UserAssignment> overdueAssignments = assignmentRepository.findByStatusAndDueDateIsNotNull(
                AssignmentStatus.OVERDUE
        );

        if (overdueAssignments.isEmpty()) {
            return;
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId());
        List<TenantUser> admins = memberships.stream()
                .filter(m -> "SUPER_ADMIN".equals(m.getRole()) || "ADMIN".equals(m.getRole()))
                .toList();

        if (admins.isEmpty()) {
            return;
        }

        for (UserAssignment assignment : overdueAssignments) {
            long overdueHours = Duration.between(assignment.getDueDate(), now).toHours();
            if (overdueHours < settings.escalationDelayHours()) {
                continue;
            }

            String message = buildAdminEscalationMessage(assignment);
            for (TenantUser admin : admins) {
                UUID adminUserId = admin.getUserId();
                String eventKey = escalationEventKey(assignment.getId(), adminUserId, settings.escalationDelayHours());
                if (notificationRepository.existsByEventKey(eventKey)) {
                    continue;
                }

                notificationService.createNotification(
                        adminUserId,
                        NotificationType.ESCALATION,
                        message,
                        eventKey
                );
                auditService.log(
                        adminUserId,
                        AuditAction.ASSIGNMENT_ESCALATION_SENT,
                        "ASSIGNMENT",
                        assignment.getId().toString(),
                        "Escalation sent to "
                                + auditMetadataService.describeUserInCurrentTenant(adminUserId)
                                + " about overdue learner "
                                + auditMetadataService.describeUserInCurrentTenant(assignment.getUserId())
                                + " on " + auditMetadataService.describeTrack(assignment.getTrackId())
                );
            }
        }
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

    private String buildLearnerReminderMessage(UserAssignment assignment, int offsetHours) {
        Track track = trackRepository.findById(assignment.getTrackId()).orElse(null);
        String trackTitle = track != null && track.getTitle() != null && !track.getTitle().isBlank()
                ? track.getTitle()
                : assignment.getTrackId().toString();
        return "Reminder: '" + trackTitle + "' is due on " + assignment.getDueDate()
                + ". This reminder was triggered " + offsetHours + " hours before the deadline.";
    }

    private String reminderEventKey(UUID assignmentId, int offsetHours) {
        return "assignment:" + assignmentId + ":reminder:" + offsetHours;
    }

    private String escalationEventKey(UUID assignmentId, UUID adminUserId, int escalationDelayHours) {
        return "assignment:" + assignmentId + ":escalation:" + adminUserId + ":" + escalationDelayHours;
    }
}

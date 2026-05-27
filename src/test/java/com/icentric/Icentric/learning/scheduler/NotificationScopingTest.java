package com.icentric.Icentric.learning.scheduler;

import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
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
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationScopingTest {

    @Mock private UserAssignmentRepository assignmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private AuditService auditService;
    @Mock private AuditMetadataService auditMetadataService;
    @Mock private UserRepository userRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private ReminderConfigService reminderConfigService;

    @InjectMocks
    private AssignmentNotificationScheduler scheduler;

    @Test
    @DisplayName("processEscalations sends alerts to super admin and authorized manager but skips unauthorized manager")
    void processEscalations_scopesNotificationRecipients() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setSlug("test-tenant");

        // Configure escalation settings
        ReminderConfigService.ReminderSettings settings = new ReminderConfigService.ReminderSettings(
                true, List.of(24), true, 24
        );
        when(reminderConfigService.resolveConfig(tenant.getId())).thenReturn(settings);

        // Setup overdue assignment
        UUID learnerId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UserAssignment assignment = new UserAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(learnerId);
        assignment.setTrackId(trackId);
        assignment.setStatus(AssignmentStatus.OVERDUE);
        assignment.setDueDate(Instant.now().minus(48, ChronoUnit.HOURS)); // well past the 24 hour escalation limit

        when(assignmentRepository.findByStatusAndDueDateIsNotNull(AssignmentStatus.OVERDUE))
                .thenReturn(List.of(assignment));

        // Setup members and admins: Super Admin, Manager 1 (onboarded the learner), Manager 2 (other manager)
        UUID superAdminId = UUID.randomUUID();
        UUID manager1Id = UUID.randomUUID();
        UUID manager2Id = UUID.randomUUID();

        TenantUser superAdmin = new TenantUser();
        superAdmin.setUserId(superAdminId);
        superAdmin.setRole("SUPER_ADMIN");

        TenantUser manager1 = new TenantUser();
        manager1.setUserId(manager1Id);
        manager1.setRole("ADMIN");

        TenantUser manager2 = new TenantUser();
        manager2.setUserId(manager2Id);
        manager2.setRole("ADMIN");

        when(tenantUserRepository.findByTenantId(tenant.getId()))
                .thenReturn(List.of(superAdmin, manager1, manager2));

        // Learner TenantUser with createdBy = Manager 1
        TenantUser learnerTenantUser = new TenantUser();
        learnerTenantUser.setUserId(learnerId);
        learnerTenantUser.setCreatedBy(manager1Id);
        learnerTenantUser.setRole("LEARNER");

        when(tenantUserRepository.findByUserIdAndTenantId(learnerId, tenant.getId()))
                .thenReturn(Optional.of(learnerTenantUser));

        // Stub lookup details
        Track track = new Track();
        track.setId(trackId);
        track.setTitle("Escalation Course");
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        User learner = new User();
        learner.setId(learnerId);
        learner.setName("Learner One");
        learner.setEmail("learner1@test.com");
        when(userRepository.findById(learnerId)).thenReturn(Optional.of(learner));

        // Ensure notifications don't already exist
        when(notificationRepository.existsByEventKey(any())).thenReturn(false);

        // Run processAssignmentsForTenant directly
        scheduler.processAssignmentsForTenant(tenant, Instant.now());

        // Verify:
        // 1. Super Admin got the notification
        verify(notificationService).createNotification(
                eq(superAdminId),
                eq(NotificationType.ESCALATION),
                contains("Learner One"),
                contains(assignment.getId().toString())
        );

        // 2. Manager 1 (onboarded the learner) got the notification
        verify(notificationService).createNotification(
                eq(manager1Id),
                eq(NotificationType.ESCALATION),
                contains("Learner One"),
                contains(assignment.getId().toString())
        );

        // 3. Manager 2 (who did NOT onboard the learner) did NOT get the notification
        verify(notificationService, never()).createNotification(
                eq(manager2Id),
                eq(NotificationType.ESCALATION),
                any(),
                any()
        );
    }
}

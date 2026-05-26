package com.icentric.Icentric.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.LaggingLearnerResponse;
import com.icentric.Icentric.learning.dto.AdminOverviewResponse;
import com.icentric.Icentric.learning.dto.DepartmentLeaderboardResponse;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.AssessmentAttempt;
import com.icentric.Icentric.learning.entity.AssessmentConfig;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.AssessmentAttemptRepository;
import com.icentric.Icentric.learning.repository.AssessmentConfigRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.audit.constants.AuditAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentKPIAndInterventionTest {

    @Mock UserRepository userRepository;
    @Mock TenantUserRepository tenantUserRepository;
    @Mock TenantRepository tenantRepository;
    @Mock UserAssignmentRepository assignmentRepository;
    @Mock AssessmentAttemptRepository assessmentAttemptRepository;
    @Mock IssuedCertificateRepository issuedCertificateRepository;
    @Mock LessonProgressRepository progressRepository;
    @Mock LessonRepository lessonRepository;
    @Mock TenantSchemaService tenantSchemaService;
    @Mock EmailService emailService;
    @Mock TrackRepository trackRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AssessmentConfigRepository assessmentConfigRepository;
    @Mock AuditService auditService;
    @Mock com.icentric.Icentric.learning.repository.AssessmentResetLogRepository assessmentResetLogRepository;

    @InjectMocks
    AdminAnalyticsService adminAnalyticsService;

    private UUID tenantId;
    private Tenant tenant;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug("test-tenant");
        TenantContext.setTenant("test-tenant");
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void setupMockSecurityContext(UUID actorId) {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getDetails()).thenReturn(actorId.toString());
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    private AssessmentConfig createConfig(String id, String title, String retakePolicy) {
        AssessmentConfig config = new AssessmentConfig();
        config.setId(id);
        ObjectNode root = mapper.createObjectNode();
        root.put("title", title);
        ObjectNode cfg = mapper.createObjectNode();
        cfg.put("retakePolicy", retakePolicy);
        root.set("config", cfg);
        config.setConfigData(root);
        return config;
    }

    private TenantUser createMembership(UUID userId, String role, UUID createdBy) {
        TenantUser tu = new TenantUser();
        tu.setUserId(userId);
        tu.setTenantId(tenantId);
        tu.setRole(role);
        tu.setDepartment(Department.ENGINEERING);
        tu.setCreatedBy(createdBy);
        return tu;
    }

    private User createUser(UUID id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    private AssessmentAttempt createAttempt(UUID userId, String configId, String status, int score, int attemptNum) {
        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setUserId(userId);
        attempt.setAssessmentConfigId(configId);
        attempt.setStatus(status);
        attempt.setScore(score);
        attempt.setAttemptNumber(attemptNum);
        attempt.setDateCompleted(Instant.now());
        return attempt;
    }

    @Test
    @DisplayName("getLaggingLearners returns empty list when no users are onboarded under Standard Manager")
    void getLaggingLearners_emptyWhenNoScopedUsers() {
        UUID managerId = UUID.randomUUID();
        setupMockSecurityContext(managerId);

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        // The manager has an ADMIN role membership in the tenant
        TenantUser managerMembership = createMembership(managerId, "ADMIN", null);
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId))
                .thenReturn(Optional.of(managerMembership));

        // No learners in the tenant
        when(tenantUserRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<LaggingLearnerResponse> result = adminAnalyticsService.getLaggingLearners();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getLaggingLearners scopes learners by creator for Standard Manager and includes only locked out failed learners")
    void getLaggingLearners_scopesByCreatorForStandardManager() {
        UUID managerId = UUID.randomUUID();
        setupMockSecurityContext(managerId);

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        TenantUser managerMembership = createMembership(managerId, "ADMIN", null);
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId))
                .thenReturn(Optional.of(managerMembership));

        // Let's create two learners:
        // Learner 1: onboarded by managerId. Has 2 failed attempts, retakePolicy is 2. (Lagging!)
        // Learner 2: onboarded by another manager. Has 2 failed attempts. (Should be filtered out!)
        // Learner 3: onboarded by managerId. Has 1 failed attempt, retakePolicy is 2. (Not lagging yet!)
        // Learner 4: onboarded by managerId. Has 2 attempts, but passed the 2nd. (Not lagging!)
        UUID learner1Id = UUID.randomUUID();
        UUID learner2Id = UUID.randomUUID();
        UUID learner3Id = UUID.randomUUID();
        UUID learner4Id = UUID.randomUUID();

        TenantUser tu1 = createMembership(learner1Id, "LEARNER", managerId);
        TenantUser tu2 = createMembership(learner2Id, "LEARNER", UUID.randomUUID());
        TenantUser tu3 = createMembership(learner3Id, "LEARNER", managerId);
        TenantUser tu4 = createMembership(learner4Id, "LEARNER", managerId);

        when(tenantUserRepository.findByTenantId(tenantId))
                .thenReturn(List.of(managerMembership, tu1, tu2, tu3, tu4));

        User user1 = createUser(learner1Id, "Learner One", "l1@icentric.com");
        User user3 = createUser(learner3Id, "Learner Three", "l3@icentric.com");
        User user4 = createUser(learner4Id, "Learner Four", "l4@icentric.com");
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user1, user3, user4));

        // Create a finite retake assessment config (limit = 2)
        String configId = "assessment-123";
        AssessmentConfig config = createConfig(configId, "Compliance Assessment", "2");
        when(assessmentConfigRepository.findAll()).thenReturn(List.of(config));

        // Mock attempts
        // Learner 1: 2 failed attempts
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learner1Id, configId))
                .thenReturn(List.of(
                        createAttempt(learner1Id, configId, "FAILED", 40, 1),
                        createAttempt(learner1Id, configId, "FAILED", 55, 2)
                ));

        // Learner 3: 1 failed attempt (limit is 2, so not lagging yet)
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learner3Id, configId))
                .thenReturn(List.of(
                        createAttempt(learner3Id, configId, "FAILED", 45, 1)
                ));

        // Learner 4: 2 attempts, 1st failed, 2nd passed
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learner4Id, configId))
                .thenReturn(List.of(
                        createAttempt(learner4Id, configId, "FAILED", 50, 1),
                        createAttempt(learner4Id, configId, "PASSED", 85, 2)
                ));

        List<LaggingLearnerResponse> result = adminAnalyticsService.getLaggingLearners();

        // Should only return Learner 1
        assertThat(result).hasSize(1);
        LaggingLearnerResponse response = result.getFirst();
        assertThat(response.userId()).isEqualTo(learner1Id);
        assertThat(response.userName()).isEqualTo("Learner One");
        assertThat(response.attemptCount()).isEqualTo(2);
        assertThat(response.maxAttemptsAllowed()).isEqualTo(2);
        assertThat(response.lastScore()).isEqualTo(55);
        assertThat(response.assessmentTitle()).isEqualTo("Compliance Assessment");
    }

    @Test
    @DisplayName("getLaggingLearners includes all learners in the tenant for Super Admin")
    void getLaggingLearners_includesAllLearnersForSuperAdmin() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        TenantUser adminMembership = createMembership(adminId, "SUPER_ADMIN", null);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenantId))
                .thenReturn(Optional.of(adminMembership));

        // Both onboarded by different managers, but visible to Super Admin
        UUID learner1Id = UUID.randomUUID();
        UUID learner2Id = UUID.randomUUID();

        TenantUser tu1 = createMembership(learner1Id, "LEARNER", UUID.randomUUID());
        TenantUser tu2 = createMembership(learner2Id, "LEARNER", UUID.randomUUID());

        when(tenantUserRepository.findByTenantId(tenantId))
                .thenReturn(List.of(adminMembership, tu1, tu2));

        User user1 = createUser(learner1Id, "Learner One", "l1@icentric.com");
        User user2 = createUser(learner2Id, "Learner Two", "l2@icentric.com");
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user1, user2));

        String configId = "assessment-123";
        AssessmentConfig config = createConfig(configId, "Compliance Assessment", "1");
        when(assessmentConfigRepository.findAll()).thenReturn(List.of(config));

        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learner1Id, configId))
                .thenReturn(List.of(createAttempt(learner1Id, configId, "FAILED", 40, 1)));
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learner2Id, configId))
                .thenReturn(List.of(createAttempt(learner2Id, configId, "FAILED", 30, 1)));

        List<LaggingLearnerResponse> result = adminAnalyticsService.getLaggingLearners();

        // Super Admin sees both as lagging learners
        assertThat(result).hasSize(2);
        List<UUID> laggingUserIds = result.stream().map(LaggingLearnerResponse::userId).toList();
        assertThat(laggingUserIds).containsExactlyInAnyOrder(learner1Id, learner2Id);
    }

    @Test
    @DisplayName("resetAttempts permits Standard Manager to reset attempt history of their onboarded learner")
    void resetAttempts_allowsStandardManagerForOnboardedLearner() {
        UUID managerId = UUID.randomUUID();
        setupMockSecurityContext(managerId);

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        TenantUser managerMembership = createMembership(managerId, "ADMIN", null);
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId))
                .thenReturn(Optional.of(managerMembership));

        UUID learnerId = UUID.randomUUID();
        TenantUser targetMembership = createMembership(learnerId, "LEARNER", managerId);
        when(tenantUserRepository.findByUserIdAndTenantId(learnerId, tenantId))
                .thenReturn(Optional.of(targetMembership));

        String configId = "assessment-123";
        AssessmentAttempt attempt1 = createAttempt(learnerId, configId, "FAILED", 45, 1);
        AssessmentAttempt attempt2 = createAttempt(learnerId, configId, "FAILED", 50, 2);
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learnerId, configId))
                .thenReturn(List.of(attempt1, attempt2));

        adminAnalyticsService.resetAttempts(learnerId, configId);

        // Verification
        verify(assessmentAttemptRepository).deleteAll(anyList());
        verify(auditService).log(
                eq(managerId),
                eq(AuditAction.ASSESSMENT_RESET_ATTEMPTS),
                eq("ASSESSMENT"),
                eq(configId),
                contains("Reset final assessment attempts for user ID " + learnerId)
        );
    }

    @Test
    @DisplayName("resetAttempts throws AccessDeniedException when Standard Manager resets learner they did not onboard")
    void resetAttempts_throwsAccessDeniedForNonOnboardedLearner() {
        UUID managerId = UUID.randomUUID();
        setupMockSecurityContext(managerId);

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        TenantUser managerMembership = createMembership(managerId, "ADMIN", null);
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId))
                .thenReturn(Optional.of(managerMembership));

        // Learner onboarded by someone else
        UUID learnerId = UUID.randomUUID();
        TenantUser targetMembership = createMembership(learnerId, "LEARNER", UUID.randomUUID());
        when(tenantUserRepository.findByUserIdAndTenantId(learnerId, tenantId))
                .thenReturn(Optional.of(targetMembership));

        String configId = "assessment-123";

        assertThatThrownBy(() -> adminAnalyticsService.resetAttempts(learnerId, configId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied: You are not authorized to reset attempts");

        verifyNoInteractions(assessmentAttemptRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("resetAttempts allows Super Admin to reset attempt history of any learner in tenant")
    void resetAttempts_allowsSuperAdminForAnyLearner() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        TenantUser adminMembership = createMembership(adminId, "SUPER_ADMIN", null);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenantId))
                .thenReturn(Optional.of(adminMembership));

        UUID learnerId = UUID.randomUUID();
        TenantUser targetMembership = createMembership(learnerId, "LEARNER", UUID.randomUUID());
        when(tenantUserRepository.findByUserIdAndTenantId(learnerId, tenantId))
                .thenReturn(Optional.of(targetMembership));

        String configId = "assessment-123";
        AssessmentAttempt attempt = createAttempt(learnerId, configId, "FAILED", 45, 1);
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(learnerId, configId))
                .thenReturn(List.of(attempt));

        adminAnalyticsService.resetAttempts(learnerId, configId);

        verify(assessmentAttemptRepository).deleteAll(anyList());
        verify(auditService).log(
                eq(adminId),
                eq(AuditAction.ASSESSMENT_RESET_ATTEMPTS),
                eq("ASSESSMENT"),
                eq(configId),
                contains("Reset final assessment attempts for user ID " + learnerId)
        );
    }

    @Test
    @DisplayName("cleanAuditDetail successfully strips emails, department metadata, role metadata, UUIDs, and cleans extra spacing")
    void cleanAuditDetail_removesMetadataAndCleansSpacing() {
        String raw = "Escalation sent to Admin User <admin@icentric.com> [f47ac10b-58cc-4372-a567-0e02b2c3d479], department=Sales, role=ADMIN about overdue learner Learner Name <learner@icentric.com> [f47ac10b-58cc-4372-a567-0e02b2c3d480], department=Sales, role=LEARNER on Security Awareness [f47ac10b-58cc-4372-a567-0e02b2c3d481]";
        String cleaned = adminAnalyticsService.cleanAuditDetail(raw);
        assertThat(cleaned).isEqualTo("Escalation sent to Admin User about overdue learner Learner Name on Security Awareness");
    }

    @Test
    @DisplayName("buildActivityFeed uses clean details for ASSIGNMENT_ESCALATION_SENT and other major actions")
    void buildActivityFeed_usesCleanDetailsForEscalationsAndOtherActions() {
        Tenant tenant = new Tenant();
        tenant.setSlug("test-tenant");
        tenant.setId(UUID.randomUUID());

        com.icentric.Icentric.audit.entity.AuditLog log1 = new com.icentric.Icentric.audit.entity.AuditLog();
        log1.setId(UUID.randomUUID());
        log1.setAction(AuditAction.ASSIGNMENT_ESCALATION_SENT);
        log1.setDetails("Escalation sent to Admin User <admin@icentric.com> [f47ac10b-58cc-4372-a567-0e02b2c3d479], department=Sales, role=ADMIN about overdue learner Learner Name <learner@icentric.com> [f47ac10b-58cc-4372-a567-0e02b2c3d480], department=Sales, role=LEARNER on Security Awareness [f47ac10b-58cc-4372-a567-0e02b2c3d481]");
        log1.setCreatedAt(Instant.now());

        com.icentric.Icentric.audit.entity.AuditLog log2 = new com.icentric.Icentric.audit.entity.AuditLog();
        log2.setId(UUID.randomUUID());
        log2.setAction(AuditAction.CERTIFICATE_ISSUED);
        log2.setDetails("Issued certificate to Jane Smith <jane@smith.com> [f47ac10b-58cc-4372-a567-0e02b2c3d480], department=HR, role=LEARNER for Cybersecurity Basics [f47ac10b-58cc-4372-a567-0e02b2c3d481]. Generation queued asynchronously.");
        log2.setCreatedAt(Instant.now());

        com.icentric.Icentric.audit.entity.AuditLog log3 = new com.icentric.Icentric.audit.entity.AuditLog();
        log3.setId(UUID.randomUUID());
        log3.setAction(AuditAction.ASSIGNMENT_OVERDUE);
        log3.setDetails("Learner Name <learner@icentric.com> [f47ac10b-58cc-4372-a567-0e02b2c3d480], department=Sales, role=LEARNER became overdue on Cybersecurity Basics [f47ac10b-58cc-4372-a567-0e02b2c3d481] with due date 2026-05-26T17:57:30Z");
        log3.setCreatedAt(Instant.now());

        org.springframework.data.domain.Page<com.icentric.Icentric.audit.entity.AuditLog> page = new org.springframework.data.domain.PageImpl<>(List.of(log1, log2, log3));
        when(auditLogRepository.findByTenantSlugAndActionIn(any(), any(), any())).thenReturn(page);

        List<AdminOverviewResponse.ActivityItem> feed = adminAnalyticsService.buildActivityFeed(
                tenant,
                Instant.now(),
                Map.of(),
                List.of(),
                null
        );

        assertThat(feed).hasSize(3);
        assertThat(feed.get(0).text()).isEqualTo("Escalation sent to Admin User about overdue learner Learner Name on Security Awareness");
        assertThat(feed.get(0).type()).isEqualTo(AdminOverviewResponse.ActivityItem.Type.WARNING);

        assertThat(feed.get(1).text()).isEqualTo("Certificate issued to Jane Smith for Cybersecurity Basics");
        assertThat(feed.get(1).type()).isEqualTo(AdminOverviewResponse.ActivityItem.Type.SUCCESS);

        assertThat(feed.get(2).text()).isEqualTo("Learner Name became overdue on Cybersecurity Basics");
        assertThat(feed.get(2).type()).isEqualTo(AdminOverviewResponse.ActivityItem.Type.WARNING);
    }

    @Test
    @DisplayName("getDepartmentLeaderboard calculates dynamic composite scores, sorts, ranks and tags statuses correctly")
    void getDepartmentLeaderboard_calculatesRanksAndScoresCorrectly() {
        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        // Mock completion stats
        List<Object[]> completionData = List.of(
                new Object[]{Department.ENGINEERING, 10L, 9L},  // 90% completion
                new Object[]{Department.SALES, 10L, 4L}        // 40% completion
        );
        when(assignmentRepository.fetchDepartmentStats(tenantId, AssignmentStatus.COMPLETED, null))
                .thenReturn(completionData);

        // Mock quiz performance stats
        List<Object[]> performanceData = List.of(
                new Object[]{Department.ENGINEERING, 85.5, 0.95}, // 85.5% avg score, 95% pass rate
                new Object[]{Department.SALES, 60.0, 0.50}        // 60% avg score, 50% pass rate
        );
        when(assessmentAttemptRepository.getAssessmentPerformanceByDepartment(tenantId, null))
                .thenReturn(performanceData);

        DepartmentLeaderboardResponse response = adminAnalyticsService.getDepartmentLeaderboard();

        assertThat(response.rankings()).hasSize(2);

        // First rank: Engineering
        DepartmentLeaderboardResponse.LeaderboardRow rank1 = response.rankings().get(0);
        assertThat(rank1.rank()).isEqualTo(1);
        assertThat(rank1.departmentDisplayName()).isEqualTo("Engineering");
        // Score: (90 * 0.6) + (85.5 * 0.3) + (95.0 * 0.1) = 54.0 + 25.65 + 9.5 = 89.15 -> rounds to 89.2
        assertThat(rank1.leaderboardScore()).isEqualTo(89.2);
        assertThat(rank1.completionRatePercent()).isEqualTo(90.0);
        assertThat(rank1.averageQuizScorePercent()).isEqualTo(85.5);
        assertThat(rank1.activeStatus()).isEqualTo("LEADER");

        // Second rank: Sales
        DepartmentLeaderboardResponse.LeaderboardRow rank2 = response.rankings().get(1);
        assertThat(rank2.rank()).isEqualTo(2);
        assertThat(rank2.departmentDisplayName()).isEqualTo("Sales");
        // Score: (40 * 0.6) + (60 * 0.3) + (50 * 0.1) = 24 + 18 + 5 = 47.0
        assertThat(rank2.leaderboardScore()).isEqualTo(47.0);
        assertThat(rank2.completionRatePercent()).isEqualTo(40.0);
        assertThat(rank2.averageQuizScorePercent()).isEqualTo(60.0);
        assertThat(rank2.activeStatus()).isEqualTo("FALLING_BEHIND"); // Under 50% completion
    }
}

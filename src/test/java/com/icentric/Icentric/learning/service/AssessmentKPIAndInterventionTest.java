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
}

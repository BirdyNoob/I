package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.dto.BulkUploadConfirmRequest;
import com.icentric.Icentric.identity.dto.BulkUploadRowDto;
import com.icentric.Icentric.identity.dto.BulkUploadValidateResponse;
import com.icentric.Icentric.identity.dto.CsvRowValidationResult;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.service.PlaywrightPdfService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import com.icentric.Icentric.identity.dto.BulkUploadResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AuditMetadataService auditMetadataService;
    @Mock private TenantAccessGuard tenantAccessGuard;
    @Mock private EmailService emailService;
    @Mock private TrackRepository trackRepository;
    @Mock private UserAssignmentRepository userAssignmentRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private IssuedCertificateRepository issuedCertificateRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private PlaywrightPdfService playwrightPdfService;

    private UserService userService;
    private Tenant tenant;

    @BeforeEach
    void setup() {
        userService = new UserService(
                userRepository,
                tenantUserRepository,
                passwordEncoder,
                "ChangeMe@123",
                auditService,
                auditMetadataService,
                tenantAccessGuard,
                emailService,
                "http://localhost:8080",
                trackRepository,
                userAssignmentRepository,
                lessonProgressRepository,
                issuedCertificateRepository,
                lessonRepository,
                tenantSchemaService,
                playwrightPdfService
        );

        tenant = new Tenant("acme", "Acme Corp");
        TenantContext.setTenant("acme");
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createUser stores normalized name and returns it in response")
    void createUser_includesName() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.findByEmail("aryan@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        CreateUserRequest request = new CreateUserRequest(
                "  Aryan Kundal  ",
                "aryan@example.com",
                "secret123",
                "LEARNER",
                Department.ENGINEERING,
                null,
                false
        );

        var response = userService.createUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getName()).isEqualTo("Aryan Kundal");
        assertThat(response.name()).isEqualTo("Aryan Kundal");
        assertThat(response.email()).isEqualTo("aryan@example.com");
        assertThat(response.role()).isEqualTo("LEARNER");
        assertThat(response.department()).isEqualTo(Department.ENGINEERING);
    }

    @Test
    @DisplayName("createUser sends custom Icentric_Email_Manager_Welcome template for role ADMIN")
    void createUser_sendsManagerWelcomeEmail() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        CreateUserRequest request = new CreateUserRequest(
                "Manager User",
                "manager@example.com",
                "secret123",
                "ADMIN",
                Department.ENGINEERING,
                null,
                false
        );

        userService.createUser(request);

        ArgumentCaptor<java.util.Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(emailService).sendTemplateEmail(
                eq("manager@example.com"),
                eq("Welcome to Icentric — Your Manager account is ready"),
                eq("Icentric_Email_Manager_Welcome"),
                variablesCaptor.capture()
        );

        java.util.Map<String, Object> vars = variablesCaptor.getValue();
        assertThat(vars.get("userName")).isEqualTo("Manager User");
        assertThat(vars.get("tenantName")).isEqualTo("Acme Corp");
        assertThat(vars.get("managerEmail")).isEqualTo("manager@example.com");
        assertThat(vars.get("password")).isEqualTo("secret123");
        assertThat(vars.get("portalUrl")).isEqualTo("acme.icentric.com");
        assertThat(vars.get("loginUrl")).isEqualTo("http://localhost:8080/login?tenant=acme");
    }

    @Test
    @DisplayName("createUser sends AISafe_Email_TenantAdmin_Welcome template for role SUPER_ADMIN")
    void createUser_sendsSuperAdminWelcomeEmail() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.findByEmail("super@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        CreateUserRequest request = new CreateUserRequest(
                "Super User",
                "super@example.com",
                "secret123",
                "SUPER_ADMIN",
                Department.ENGINEERING,
                null,
                false
        );

        userService.createUser(request);

        ArgumentCaptor<java.util.Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(emailService).sendTemplateEmail(
                eq("super@example.com"),
                eq("Welcome to AISafe - Administrator Account Created"),
                eq("AISafe_Email_TenantAdmin_Welcome"),
                variablesCaptor.capture()
        );

        java.util.Map<String, Object> vars = variablesCaptor.getValue();
        assertThat(vars.get("userName")).isEqualTo("Super User");
        assertThat(vars.get("tenantName")).isEqualTo("Acme Corp");
        assertThat(vars.get("adminEmail")).isEqualTo("super@example.com");
        assertThat(vars.get("adminPassword")).isEqualTo("secret123");
        assertThat(vars.get("portalUrl")).isEqualTo("acme.icentric.com");
        assertThat(vars.get("loginUrl")).isEqualTo("http://localhost:8080/login?tenant=acme");
    }

    @Test
    @DisplayName("updateUser updates name when present")
    void updateUser_updatesName() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setName("Old Name");
        user.setEmail("aryan@example.com");
        user.setIsActive(true);
        user.setCreatedAt(Instant.now());

        TenantUser mapping = new TenantUser(userId, tenant.getId(), "LEARNER");
        mapping.setDepartment(Department.ENGINEERING);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tenantUserRepository.findByUserIdAndTenantId(userId, tenant.getId()))
                .thenReturn(Optional.of(mapping));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        var response = userService.updateUser(
                userId,
                new UpdateUserRequest("  New Name  ", null, null, null, null)
        );

        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(response.name()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("bulkUploadUsers accepts CSV files with UTF-8 BOM header")
    void bulkUploadUsers_acceptsUtf8BomHeader() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                "\uFEFFname,email,role,department\nAryan,aryan@example.com,LEARNER,Engineering\n".getBytes()
        );

        when(passwordEncoder.encode("ChangeMe@123")).thenReturn("encoded-default-password");
        when(userRepository.findAllByEmailLowerIn(Collections.singleton("aryan@example.com")))
                .thenReturn(Collections.emptyList());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        var response = userService.bulkUploadUsers(file, true);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.success()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    @DisplayName("bulkUploadUsers keeps invalid header errors as bad request input")
    void bulkUploadUsers_rethrowsInvalidHeaderAsIllegalArgument() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                "full_name,email,role,department\nAryan,aryan@example.com,LEARNER,Engineering\n".getBytes()
        );

        assertThatThrownBy(() -> userService.bulkUploadUsers(file, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV must contain name, email, role, and department headers");
    }

    @Test
    @DisplayName("validateBulkUpload correctly reports formatting, scoping, and conflict errors")
    void validateBulkUpload_reportsFormattingScopingAndConflicts() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        UUID managerId = UUID.randomUUID();
        setupMockSecurityContext(managerId);

        TenantUser managerMapping = new TenantUser(managerId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenant.getId()))
                .thenReturn(Optional.of(managerMapping));

        // Rows in CSV:
        // 1. Valid row (learner)
        // 2. Missing name
        // 3. Invalid email format
        // 4. Duplicate email in file (row 4 duplicate of row 1)
        // 5. Invalid department name
        // 6. Existing user in tenant
        // 7. SUPER_ADMIN uploaded by ADMIN manager (unauthorized)
        String csvContent = "name,email,role,department\n" +
                "Valid User,valid@example.com,LEARNER,Engineering\n" +
                ",missingname@example.com,LEARNER,Sales\n" +
                "Bad Email,invalid-email,LEARNER,Sales\n" +
                "Dup Email,valid@example.com,LEARNER,Engineering\n" +
                "Bad Dept,baddept@example.com,LEARNER,UnknownDepartment\n" +
                "Existing User,existing@example.com,LEARNER,Engineering\n" +
                "Super User,super@example.com,SUPER_ADMIN,Engineering\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                csvContent.getBytes()
        );

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");

        when(userRepository.findAllByEmailLowerIn(any()))
                .thenReturn(List.of(existingUser));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdIn(eq(tenant.getId()), any()))
                .thenReturn(List.of(existingUser.getId()));

        BulkUploadValidateResponse response = userService.validateBulkUpload(file);

        assertThat(response.totalRows()).isEqualTo(7);
        assertThat(response.validRowsCount()).isEqualTo(1);
        assertThat(response.invalidRowsCount()).isEqualTo(6);

        List<CsvRowValidationResult> rows = response.rows();
        assertThat(rows.get(0).valid()).isTrue();
        assertThat(rows.get(0).errors()).isEmpty();

        assertThat(rows.get(1).valid()).isFalse();
        assertThat(rows.get(1).errors()).contains("Name is required");

        assertThat(rows.get(2).valid()).isFalse();
        assertThat(rows.get(2).errors()).contains("Invalid email format");

        assertThat(rows.get(3).valid()).isFalse();
        assertThat(rows.get(3).errors()).contains("Duplicate email in CSV file");

        assertThat(rows.get(4).valid()).isFalse();
        assertThat(rows.get(4).errors()).contains("Invalid department: must match a valid system department");

        assertThat(rows.get(5).valid()).isFalse();
        assertThat(rows.get(5).errors()).contains("User already exists in this tenant");

        assertThat(rows.get(6).valid()).isFalse();
        assertThat(rows.get(6).errors()).contains("Managers are not authorized to upload SUPER_ADMIN users");
    }

    @Test
    @DisplayName("confirmBulkUpload persists users, assigns tracks, and sets createdBy correctly")
    void confirmBulkUpload_persistsUsersAndSetsCreatedBy() {
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(passwordEncoder.encode("ChangeMe@123")).thenReturn("encoded-password");

        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMapping = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMapping));

        BulkUploadRowDto u1 = new BulkUploadRowDto("New User", "new@example.com", "LEARNER", "Engineering");
        BulkUploadRowDto u2 = new BulkUploadRowDto("Existing Global User", "global@example.com", "LEARNER", "Sales");

        BulkUploadConfirmRequest request = new BulkUploadConfirmRequest(List.of(u1, u2), true);

        User existingGlobal = new User();
        existingGlobal.setId(UUID.randomUUID());
        existingGlobal.setEmail("global@example.com");

        when(userRepository.findAllByEmailLowerIn(any()))
                .thenReturn(List.of(existingGlobal));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdIn(eq(tenant.getId()), any()))
                .thenReturn(List.of());

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        BulkUploadResponse response = userService.confirmBulkUpload(request);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.success()).isEqualTo(2);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(response.errors()).isEmpty();

        // Verify that global user was created for new email
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        assertThat(userCaptor.getAllValues().stream().anyMatch(u -> "new@example.com".equals(u.getEmail()))).isTrue();

        // Verify tenant user mappings were saved with createdBy set to adminId
        ArgumentCaptor<TenantUser> tenantUserCaptor = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserRepository, atLeastOnce()).save(tenantUserCaptor.capture());
        List<TenantUser> savedMappings = tenantUserCaptor.getAllValues();
        assertThat(savedMappings).hasSize(2);
        for (TenantUser mapping : savedMappings) {
            assertThat(mapping.getCreatedBy()).isEqualTo(adminId);
        }
    }


    private void setupMockSecurityContext(UUID actorId) {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getDetails()).thenReturn(actorId.toString());
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("createUser by ADMIN sets createdBy and allows null department")
    void createUser_byAdmin_setsCreatedByAndAllowsNullDepartment() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0, User.class));
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> inv.getArgument(0, TenantUser.class));

        CreateUserRequest request = new CreateUserRequest(
                "Learner Null Dept",
                "learner@example.com",
                "secret123",
                "LEARNER",
                null,
                null,
                false
        );

        var response = userService.createUser(request);

        ArgumentCaptor<TenantUser> tenantUserCaptor = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserRepository).save(tenantUserCaptor.capture());

        TenantUser savedMapping = tenantUserCaptor.getValue();
        assertThat(savedMapping.getCreatedBy()).isEqualTo(adminId);
        assertThat(savedMapping.getDepartment()).isNull();
        assertThat(response.role()).isEqualTo("LEARNER");
        assertThat(response.department()).isNull();
    }

    @Test
    @DisplayName("createUser by ADMIN prevents SUPER_ADMIN creation")
    void createUser_byAdmin_preventSuperAdminCreation() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        CreateUserRequest request = new CreateUserRequest(
                "Super Admin Attempt",
                "sa@example.com",
                "secret123",
                "SUPER_ADMIN",
                null,
                null,
                false
        );

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Managers are not authorized to create SUPER_ADMIN users");
    }

    @Test
    @DisplayName("updateUser by ADMIN on non-onboarded user throws AccessDeniedException")
    void updateUser_byAdmin_onNonOnboardedUser_throwsAccessDenied() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        UUID targetUserId = UUID.randomUUID();
        User targetUser = new User();
        targetUser.setId(targetUserId);

        TenantUser targetMapping = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMapping.setCreatedBy(UUID.randomUUID()); // owned by someone else

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId()))
                .thenReturn(Optional.of(targetMapping));

        UpdateUserRequest updateRequest = new UpdateUserRequest("Updated Name", null, null, null, null);

        assertThatThrownBy(() -> userService.updateUser(targetUserId, updateRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to modify this user");
    }

    @Test
    @DisplayName("updateUser by ADMIN prevents promoting to SUPER_ADMIN")
    void updateUser_byAdmin_preventSuperAdminPromotion() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        UUID targetUserId = UUID.randomUUID();
        User targetUser = new User();
        targetUser.setId(targetUserId);

        TenantUser targetMapping = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMapping.setCreatedBy(adminId); // owned by this admin

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId()))
                .thenReturn(Optional.of(targetMapping));

        UpdateUserRequest updateRequest = new UpdateUserRequest(null, "SUPER_ADMIN", null, null, null);

        assertThatThrownBy(() -> userService.updateUser(targetUserId, updateRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Managers are not authorized to promote users to SUPER_ADMIN");
    }

    @Test
    @DisplayName("deactivateUser by ADMIN on non-onboarded user throws AccessDeniedException")
    void deactivateUser_byAdmin_onNonOnboardedUser_throwsAccessDenied() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        UUID targetUserId = UUID.randomUUID();
        User targetUser = new User();
        targetUser.setId(targetUserId);

        TenantUser targetMapping = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMapping.setCreatedBy(UUID.randomUUID()); // owned by someone else

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId()))
                .thenReturn(Optional.of(targetMapping));

        assertThatThrownBy(() -> userService.deactivateUser(targetUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deactivate this user");
    }

    @Test
    @DisplayName("getUsers by ADMIN filters by createdBy")
    void getUsers_byAdmin_passesCreatedByFilter() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        Pageable pageable = PageRequest.of(0, 10);
        Page<UserResponse> expectedPage = new PageImpl<>(List.of());

        when(userRepository.findTenantUsers(tenant.getId(), adminId, null, null, null, pageable))
                .thenReturn(expectedPage);

        var result = userService.getUsers(null, null, null, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                tenantSchemaService
        );

        tenant = new Tenant("acme", "Acme Corp");
        TenantContext.setTenant("acme");
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
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
        assertThat(response.department()).isEqualTo("Engineering");
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
        when(tenantUserRepository.findByTenantId(tenant.getId())).thenReturn(List.of());
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
}

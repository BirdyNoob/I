package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantSchemaService tenantSchemaService;

    @Test
    @DisplayName("createUser stores normalized name and returns it in response")
    void createUser_includesName() {
        UserService userService = new UserService(
                repository,
                passwordEncoder,
                tenantSchemaService,
                "ChangeMe@123"
        );

        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

        CreateUserRequest request = new CreateUserRequest(
                "  Aryan Kundal  ",
                "aryan@example.com",
                "secret123",
                "LEARNER",
                "Engineering"
        );

        var response = userService.createUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(userCaptor.capture());
        verify(tenantSchemaService).applyCurrentTenantSearchPath();

        assertThat(userCaptor.getValue().getName()).isEqualTo("Aryan Kundal");
        assertThat(response.name()).isEqualTo("Aryan Kundal");
        assertThat(response.email()).isEqualTo("aryan@example.com");
    }

    @Test
    @DisplayName("updateUser updates name when present")
    void updateUser_updatesName() {
        UserService userService = new UserService(
                repository,
                passwordEncoder,
                tenantSchemaService,
                "ChangeMe@123"
        );

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setName("Old Name");
        user.setEmail("aryan@example.com");
        user.setRole("LEARNER");
        user.setDepartment("Engineering");
        user.setIsActive(true);
        user.setCreatedAt(Instant.now());

        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

        var response = userService.updateUser(
                userId,
                new UpdateUserRequest("  New Name  ", null, null, null)
        );

        verify(tenantSchemaService).applyCurrentTenantSearchPath();
        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(response.name()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("bulkUploadUsers accepts CSV files with UTF-8 BOM header")
    void bulkUploadUsers_acceptsUtf8BomHeader() {
        UserService userService = new UserService(
                repository,
                passwordEncoder,
                tenantSchemaService,
                "ChangeMe@123"
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                "\uFEFFname,email,role,department\nAryan,aryan@example.com,LEARNER,Engineering\n".getBytes()
        );

        when(passwordEncoder.encode("ChangeMe@123")).thenReturn("encoded-default-password");
        when(repository.findAllByEmailLowerIn(Collections.singleton("aryan@example.com"))).thenReturn(Collections.emptyList());

        var response = userService.bulkUploadUsers(file);

        verify(tenantSchemaService).applyCurrentTenantSearchPath();
        verify(repository).saveAll(any());
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.success()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    @DisplayName("bulkUploadUsers keeps invalid header errors as bad request input")
    void bulkUploadUsers_rethrowsInvalidHeaderAsIllegalArgument() {
        UserService userService = new UserService(
                repository,
                passwordEncoder,
                tenantSchemaService,
                "ChangeMe@123"
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                "full_name,email,role,department\nAryan,aryan@example.com,LEARNER,Engineering\n".getBytes()
        );

        assertThatThrownBy(() -> userService.bulkUploadUsers(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV must contain name, email, role, and department headers");
    }
}

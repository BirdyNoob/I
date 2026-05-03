package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.security.RefreshToken;
import com.icentric.Icentric.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock TenantUserRepository tenantUserRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AuditService auditService;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks AuthService authService;

    private User user;
    private Tenant tenant;
    private TenantUser tenantUser;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("learner@acme.com");
        user.setPasswordHash("hashed-pw");
        user.setIsActive(true);

        tenant = new Tenant("acme", "Acme Corp");

        tenantUser = new TenantUser(user.getId(), tenant.getId(), "LEARNER");
    }

    @Test
    @DisplayName("login succeeds with single tenant → returns access + refresh token")
    void loginSuccess_singleTenant() {
        when(userRepository.findByEmail("learner@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
        when(tenantUserRepository.findByUserId(user.getId())).thenReturn(List.of(tenantUser));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(jwtService.generateToken(any(), any(), eq("ROLE_LEARNER"), eq("acme")))
                .thenReturn("jwt-access");
        when(refreshTokenService.create(any(), any(), any(), any()))
                .thenReturn("opaque-refresh");

        LoginResponse response = authService.login(
                new LoginRequest("learner@acme.com", "secret"));

        assertThat(response.accessToken()).isEqualTo("jwt-access");
        assertThat(response.refreshToken()).isEqualTo("opaque-refresh");
        assertThat(response.tenants()).isNull();
        verify(auditService).log(any(), eq(AuditAction.LOGIN), eq("USER"), any(), any());
    }

    @Test
    @DisplayName("login with wrong password throws BadCredentialsException")
    void loginFails_wrongPassword() {
        when(userRepository.findByEmail("learner@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("learner@acme.com", "bad")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login with non-existent email throws BadCredentialsException")
    void loginFails_emailNotFound() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("no@one.com", "pw")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login succeeds with multiple tenants → returns tenant choices")
    void loginSuccess_multiTenant() {
        Tenant tenant2 = new Tenant("beta", "Beta Corp");
        TenantUser tenantUser2 = new TenantUser(user.getId(), tenant2.getId(), "ADMIN");

        when(userRepository.findByEmail("learner@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
        when(tenantUserRepository.findByUserId(user.getId()))
                .thenReturn(List.of(tenantUser, tenantUser2));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(tenantRepository.findById(tenant2.getId())).thenReturn(Optional.of(tenant2));

        LoginResponse response = authService.login(
                new LoginRequest("learner@acme.com", "secret"));

        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.tenants()).hasSize(2);
    }

    @Test
    @DisplayName("refresh rotates tokens — old revoked, new pair returned")
    void refreshRotatesTokens() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken(
                "old-token", userId, "learner@acme.com", "ROLE_LEARNER",
                "acme", Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshTokenService.validate("old-token")).thenReturn(stored);
        when(jwtService.generateToken(any(), any(), any(), any()))
                .thenReturn("new-jwt");
        when(refreshTokenService.create(any(), any(), any(), any()))
                .thenReturn("new-refresh");

        LoginResponse response = authService.refresh("old-token");

        assertThat(response.accessToken()).isEqualTo("new-jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).revoke("old-token");
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logoutRevokesToken() {
        authService.logout("the-token");
        verify(refreshTokenService).revoke("the-token");
    }
}

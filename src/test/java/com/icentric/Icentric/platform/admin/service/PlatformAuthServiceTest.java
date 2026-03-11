package com.icentric.Icentric.platform.admin.service;

import com.icentric.Icentric.platform.admin.dto.PlatformLoginRequest;
import com.icentric.Icentric.platform.admin.dto.PlatformLoginResponse;
import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.security.MfaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceTest {

    @Mock
    PlatformAdminRepository repository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;
    @Mock
    MfaService mfaService;

    @InjectMocks
    PlatformAuthService authService;

    private PlatformAdmin admin;

    @BeforeEach
    void setup() {
        admin = new PlatformAdmin("admin@example.com", "hashed-pw", "Test Admin");
        admin.setMfaEnabled(false);
    }

    @Test
    @DisplayName("login succeeds with correct credentials (no MFA)")
    void loginSuccess_noMfa() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
        when(jwtService.generateToken(any(), any(), eq("ROLE_PLATFORM_ADMIN"), eq("system")))
                .thenReturn("jwt-token");

        PlatformLoginResponse response = authService.login(
                new PlatformLoginRequest("admin@example.com", "secret", null));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        verify(mfaService, never()).verifyCode(any(), any());
    }

    @Test
    @DisplayName("login fails when email not found → 401")
    void loginFails_emailNotFound() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new PlatformLoginRequest("nope@x.com", "pw", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login fails when password is wrong → 401")
    void loginFails_wrongPassword() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new PlatformLoginRequest("admin@example.com", "bad", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login fails when MFA is enabled but code is invalid → 401")
    void loginFails_badMfaCode() {
        admin.setMfaEnabled(true);
        admin.setMfaSecret("totp-secret");

        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
        when(mfaService.verifyCode("totp-secret", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new PlatformLoginRequest("admin@example.com", "secret", "000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MFA code");
    }

    @Test
    @DisplayName("login succeeds with MFA enabled and valid code")
    void loginSuccess_withMfa() {
        admin.setMfaEnabled(true);
        admin.setMfaSecret("totp-secret");

        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
        when(mfaService.verifyCode("totp-secret", "123456")).thenReturn(true);
        when(jwtService.generateToken(any(), any(), any(), any())).thenReturn("jwt-token");

        PlatformLoginResponse response = authService.login(
                new PlatformLoginRequest("admin@example.com", "secret", "123456"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
    }
}

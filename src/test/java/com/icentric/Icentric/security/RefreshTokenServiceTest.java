package com.icentric.Icentric.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repository;

    RefreshTokenService service;

    @BeforeEach
    void setup() {
        service = new RefreshTokenService(repository, 7);
    }

    @Test
    @DisplayName("create generates and persists a refresh token")
    void createPersistsToken() {
        UUID userId = UUID.randomUUID();

        String token = service.create(userId, "user@test.com", "ROLE_LEARNER", "acme");

        assertThat(token).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("user@test.com");
        assertThat(saved.getTenantSlug()).isEqualTo("acme");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("validate returns stored token when valid")
    void validateReturnsToken() {
        RefreshToken stored = new RefreshToken(
                "valid-token", UUID.randomUUID(), "u@t.com", "ROLE_LEARNER",
                "acme", Instant.now().plus(1, ChronoUnit.DAYS));

        when(repository.findByTokenAndRevokedFalse("valid-token"))
                .thenReturn(Optional.of(stored));

        RefreshToken result = service.validate("valid-token");
        assertThat(result.getToken()).isEqualTo("valid-token");
    }

    @Test
    @DisplayName("validate throws on missing token")
    void validateThrowsOnMissing() {
        when(repository.findByTokenAndRevokedFalse("nope"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    @DisplayName("validate throws on expired token")
    void validateThrowsOnExpired() {
        RefreshToken expired = new RefreshToken(
                "expired-token", UUID.randomUUID(), "u@t.com", "ROLE_LEARNER",
                "acme", Instant.now().minus(1, ChronoUnit.HOURS));

        when(repository.findByTokenAndRevokedFalse("expired-token"))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validate("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refresh token expired");
    }

    @Test
    @DisplayName("revoke marks the token as revoked")
    void revokeMarksToken() {
        RefreshToken token = new RefreshToken(
                "to-revoke", UUID.randomUUID(), "u@t.com", "ROLE_LEARNER",
                "acme", Instant.now().plus(1, ChronoUnit.DAYS));

        when(repository.findByTokenAndRevokedFalse("to-revoke"))
                .thenReturn(Optional.of(token));

        service.revoke("to-revoke");

        assertThat(token.isRevoked()).isTrue();
        verify(repository).save(token);
    }
}

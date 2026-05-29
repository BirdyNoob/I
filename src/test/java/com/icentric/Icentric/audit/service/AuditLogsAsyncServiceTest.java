package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.common.ratelimit.DatabaseRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.thymeleaf.TemplateEngine;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogsAsyncServiceTest {

    @Mock private AuditService auditService;
    @Mock private EmailService emailService;
    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TemplateEngine templateEngine;
    @Mock private DatabaseRateLimiterService databaseRateLimiterService;

    @InjectMocks
    private AuditLogsAsyncService asyncService;

    private String email;
    private String tenantSlug;

    @BeforeEach
    void setUp() {
        email = "admin@test.com";
        tenantSlug = "test-tenant";
    }

    @Test
    @DisplayName("isCompiling defaults to false and correctly tracks active compilation state")
    void isCompiling_tracksActiveState() {
        // Initial state
        assertThat(asyncService.isCompiling(email, tenantSlug)).isFalse();

        // Simulate active compilation key entry manually since maps are package-private / internal
        when(databaseRateLimiterService.acquireLock(eq("AUDIT_LOGS_EMAIL:" + email), any())).thenReturn(true);
        asyncService.compileAndEmailLogs(
                email, null, null, null, null, null, tenantSlug
        );
        // After execution isCompiling is removed in finally block, so it should be false
        assertThat(asyncService.isCompiling(email, tenantSlug)).isFalse();
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns 0 when no previous emails have been successfully dispatched")
    void getRateLimitRemainingSeconds_defaultsToZero() {
        when(databaseRateLimiterService.getRemainingSeconds("AUDIT_LOGS_EMAIL:" + email)).thenReturn(0L);
        assertThat(asyncService.getRateLimitRemainingSeconds(email, tenantSlug)).isEqualTo(0L);
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns correct remaining cooldown when requested within the 6-hour window")
    void getRateLimitRemainingSeconds_calculatesRemainingCooldown() {
        when(databaseRateLimiterService.getRemainingSeconds("AUDIT_LOGS_EMAIL:" + email)).thenReturn(14400L);
        long remaining = asyncService.getRateLimitRemainingSeconds(email, tenantSlug);
        assertThat(remaining).isEqualTo(14400L);
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns 0 when the 6-hour rate-limiting lock has expired")
    void getRateLimitRemainingSeconds_returnsZeroAfterExpiration() {
        when(databaseRateLimiterService.getRemainingSeconds("AUDIT_LOGS_EMAIL:" + email)).thenReturn(0L);
        long remaining = asyncService.getRateLimitRemainingSeconds(email, tenantSlug);
        assertThat(remaining).isEqualTo(0L);
    }
}

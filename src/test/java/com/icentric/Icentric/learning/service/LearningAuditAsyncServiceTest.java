package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.common.ratelimit.DatabaseRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.thymeleaf.TemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningAuditAsyncServiceTest {

    @Mock private AdminAnalyticsService adminAnalyticsService;
    @Mock private EmailService emailService;
    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TemplateEngine templateEngine;
    @Mock private AuditService auditService;
    @Mock private DatabaseRateLimiterService databaseRateLimiterService;

    private LearningAuditAsyncService service;
    private String email;
    private String tenantSlug;

    @BeforeEach
    void setUp() {
        email = "admin@test.com";
        tenantSlug = "my-tenant";
        service = new LearningAuditAsyncService(
                adminAnalyticsService,
                emailService,
                tenantSchemaService,
                userRepository,
                tenantRepository,
                templateEngine,
                auditService,
                databaseRateLimiterService
        );
    }

    @Test
    @DisplayName("isCompiling returns false initially for untracked requests")
    void isCompiling_returnsFalseInitially() {
        assertThat(service.isCompiling(email, tenantSlug)).isFalse();
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns 0 initially for untracked requests")
    void getRateLimitRemainingSeconds_returnsZeroInitially() {
        when(databaseRateLimiterService.getRemainingSeconds("LEARNING_AUDIT_EMAIL:" + email)).thenReturn(0L);
        assertThat(service.getRateLimitRemainingSeconds(email, tenantSlug)).isEqualTo(0L);
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds calculates remaining seconds when locked under 6 hours")
    void getRateLimitRemainingSeconds_returnsCorrectSecondsRemaining() {
        when(databaseRateLimiterService.getRemainingSeconds("LEARNING_AUDIT_EMAIL:" + email)).thenReturn(18000L);
        long remaining = service.getRateLimitRemainingSeconds(email, tenantSlug);
        assertThat(remaining).isEqualTo(18000L);
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns 0 once the 6 hours expire")
    void getRateLimitRemainingSeconds_returnsZeroAfterExpiry() {
        when(databaseRateLimiterService.getRemainingSeconds("LEARNING_AUDIT_EMAIL:" + email)).thenReturn(0L);
        long remaining = service.getRateLimitRemainingSeconds(email, tenantSlug);
        assertThat(remaining).isEqualTo(0L);
    }
}

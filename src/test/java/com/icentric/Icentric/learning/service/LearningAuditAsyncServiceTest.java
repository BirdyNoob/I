package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.audit.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LearningAuditAsyncServiceTest {

    @Mock private AdminAnalyticsService adminAnalyticsService;
    @Mock private EmailService emailService;
    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TemplateEngine templateEngine;
    @Mock private AuditService auditService;

    private LearningAuditAsyncService service;

    @BeforeEach
    void setUp() {
        service = new LearningAuditAsyncService(
                adminAnalyticsService,
                emailService,
                tenantSchemaService,
                userRepository,
                tenantRepository,
                templateEngine,
                auditService
        );
    }

    @Test
    @DisplayName("isCompiling returns false initially for untracked requests")
    void isCompiling_returnsFalseInitially() {
        assertThat(service.isCompiling("admin@test.com", "my-tenant")).isFalse();
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns 0 initially for untracked requests")
    void getRateLimitRemainingSeconds_returnsZeroInitially() {
        assertThat(service.getRateLimitRemainingSeconds("admin@test.com", "my-tenant")).isEqualTo(0L);
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds calculates remaining seconds when locked under 6 hours")
    void getRateLimitRemainingSeconds_returnsCorrectSecondsRemaining() {
        String jobKey = "my-tenant:admin@test.com";
        // Lock 1 hour ago
        java.time.Instant oneHourAgo = java.time.Instant.now().minusSeconds(3600);
        service.lastEmailedTimes.put(jobKey, oneHourAgo);

        long remaining = service.getRateLimitRemainingSeconds("admin@test.com", "my-tenant");
        // Expecting around 5 hours (18000 seconds), allow 10 seconds boundary buffer
        assertThat(remaining).isBetween(17900L, 18000L);
    }

    @Test
    @DisplayName("getRateLimitRemainingSeconds returns 0 once the 6 hours expire")
    void getRateLimitRemainingSeconds_returnsZeroAfterExpiry() {
        String jobKey = "my-tenant:admin@test.com";
        // Lock 7 hours ago
        java.time.Instant sevenHoursAgo = java.time.Instant.now().minusSeconds(7 * 3600);
        service.lastEmailedTimes.put(jobKey, sevenHoursAgo);

        long remaining = service.getRateLimitRemainingSeconds("admin@test.com", "my-tenant");
        assertThat(remaining).isEqualTo(0L);
    }
}

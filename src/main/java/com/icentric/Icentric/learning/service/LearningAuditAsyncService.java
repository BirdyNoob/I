package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
public class LearningAuditAsyncService {

    private final AdminAnalyticsService adminAnalyticsService;
    private final EmailService emailService;
    private final TenantSchemaService tenantSchemaService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TemplateEngine templateEngine;
    private final com.icentric.Icentric.audit.service.AuditService auditService;

    // Track active background report compilations to prevent duplicate execution spam
    private final java.util.Map<String, Boolean> activeCompilations = new java.util.concurrent.ConcurrentHashMap<>();

    // Track the last successful email timestamps per administrator email for rate limiting
    final java.util.Map<String, java.time.Instant> lastEmailedTimes = new java.util.concurrent.ConcurrentHashMap<>();

    public LearningAuditAsyncService(
            AdminAnalyticsService adminAnalyticsService,
            EmailService emailService,
            TenantSchemaService tenantSchemaService,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TemplateEngine templateEngine,
            com.icentric.Icentric.audit.service.AuditService auditService
    ) {
        this.adminAnalyticsService = adminAnalyticsService;
        this.emailService = emailService;
        this.tenantSchemaService = tenantSchemaService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.templateEngine = templateEngine;
        this.auditService = auditService;
    }

    /**
     * Checks if a compilation is currently in progress for the specified user and tenant.
     */
    public boolean isCompiling(String email, String tenantSlug) {
        return activeCompilations.containsKey(tenantSlug + ":" + email);
    }

    /**
     * Returns the remaining rate limit lock duration in seconds, or 0 if free to request.
     */
    public long getRateLimitRemainingSeconds(String email, String tenantSlug) {
        String jobKey = tenantSlug + ":" + email;
        java.time.Instant lastSent = lastEmailedTimes.get(jobKey);
        if (lastSent == null) {
            return 0L;
        }

        long secondsElapsed = java.time.Duration.between(lastSent, java.time.Instant.now()).toSeconds();
        long sixHoursInSeconds = 6 * 60 * 60; // 21600 seconds

        if (secondsElapsed < sixHoursInSeconds) {
            return sixHoursInSeconds - secondsElapsed;
        }
        return 0L;
    }

    @Async("playwrightTaskExecutor")
    @Transactional(readOnly = true)
    public void compileAndEmailReport(
            String recipientEmail,
            String search,
            String departmentFilter,
            String categoryFilter,
            String tenantSlug,
            boolean isScheduled
    ) {
        log.info("Starting asynchronous report compilation for {} on tenant {}", recipientEmail, tenantSlug);
        String jobKey = tenantSlug + ":" + recipientEmail;
        activeCompilations.put(jobKey, Boolean.TRUE);

        TenantContext.setTenant(tenantSlug);
        java.util.Optional<User> adminUserOpt = java.util.Optional.empty();
        try {
            tenantSchemaService.applyCurrentTenantSearchPath();

            // 1. Resolve administrator name, ID, and tenant company name dynamically
            adminUserOpt = userRepository.findByEmail(recipientEmail);
            String adminName = adminUserOpt.map(User::getName).orElse("Administrator");
            UUID adminUserId = adminUserOpt.map(User::getId).orElse(null);

            String tenantName = tenantRepository.findBySlug(tenantSlug)
                    .map(Tenant::getCompanyName)
                    .orElse(tenantSlug);

            byte[] attachmentBytes = null;
            String filename = null;
            boolean pdfGenerationFailed = false;

            try {
                // 2. Generate PDF bytes using Playwright and light-theme templates
                attachmentBytes = adminAnalyticsService.getLearningAuditReportPdf(search, departmentFilter, categoryFilter);
                filename = "Learning_Audit_Report_" + LocalDate.now() + ".pdf";
            } catch (Exception e) {
                log.error("Failed to generate PDF report via Playwright. Falling back to CSV data attachment.", e);
                pdfGenerationFailed = true;

                // Failsafe CSV generation
                String csvData = adminAnalyticsService.getLearningAuditReportCsv(search, departmentFilter, categoryFilter);
                attachmentBytes = csvData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                filename = "Learning_Audit_Data_Backup_" + LocalDate.now() + ".csv";
            }

            // 3. Render HTML email using our professional Thymeleaf template
            Context thymeleafContext = new Context();
            thymeleafContext.setVariable("adminName", adminName);
            thymeleafContext.setVariable("tenantName", tenantName);
            thymeleafContext.setVariable("search", search);
            thymeleafContext.setVariable("department", departmentFilter);
            thymeleafContext.setVariable("category", categoryFilter);
            thymeleafContext.setVariable("generationDate", LocalDate.now().toString());
            thymeleafContext.setVariable("isScheduled", isScheduled);
            thymeleafContext.setVariable("pdfGenerationFailed", pdfGenerationFailed);

            String htmlBody = templateEngine.process("email/Icentric_Email_Learning_Audit", thymeleafContext);

            String subject = pdfGenerationFailed 
                    ? "Corporate Learning Audit Data Export [CSV Backup] - " + LocalDate.now()
                    : "Corporate Learning Audit & Talent Excellence Report - " + LocalDate.now();

            // 4. Email PDF/CSV attachment asynchronously
            emailService.sendEmailWithAttachment(recipientEmail, subject, htmlBody, attachmentBytes, filename).join();
            log.info("Asynchronous report successfully sent to {}", recipientEmail);

            // Rate-limit lock for 6 hours
            lastEmailedTimes.put(jobKey, java.time.Instant.now());

            // Log successful email dispatch
            if (adminUserId != null) {
                String renderMode = pdfGenerationFailed ? "CSV Backup format due to Playwright rendering timeout/error" : "High-fidelity Landscape A4 PDF format";
                auditService.logForTenant(
                    adminUserId,
                    com.icentric.Icentric.audit.constants.AuditAction.AUDIT_REPORT_EMAIL_SENT,
                    "USER",
                    adminUserId.toString(),
                    "Corporate Learning Audit successfully compiled and emailed. Render format: " + renderMode,
                    tenantSlug
                );
            }

        } catch (Exception e) {
            log.error("Failed to generate and email report to {}", recipientEmail, e);
            try {
                UUID adminUserId = adminUserOpt.map(User::getId).orElse(null);
                if (adminUserId != null) {
                    auditService.logForTenant(
                        adminUserId,
                        com.icentric.Icentric.audit.constants.AuditAction.AUDIT_REPORT_EMAIL_FAILED,
                        "USER",
                        adminUserId.toString(),
                        "Corporate Learning Audit compilation or delivery failed: " + e.getMessage(),
                        tenantSlug
                    );
                }
            } catch (Exception ignored) {}
        } finally {
            activeCompilations.remove(jobKey);
            TenantContext.clear();
        }
    }
}

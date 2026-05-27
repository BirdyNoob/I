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

@Service
@Slf4j
public class LearningAuditAsyncService {

    private final AdminAnalyticsService adminAnalyticsService;
    private final EmailService emailService;
    private final TenantSchemaService tenantSchemaService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TemplateEngine templateEngine;

    public LearningAuditAsyncService(
            AdminAnalyticsService adminAnalyticsService,
            EmailService emailService,
            TenantSchemaService tenantSchemaService,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TemplateEngine templateEngine
    ) {
        this.adminAnalyticsService = adminAnalyticsService;
        this.emailService = emailService;
        this.tenantSchemaService = tenantSchemaService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.templateEngine = templateEngine;
    }

    /**
     * Generates the Learning Audit PDF report asynchronously in the background and sends a professional HTML email with the PDF attached.
     */
    @Async
    @Transactional(readOnly = true)
    public void compileAndEmailReport(
            String recipientEmail,
            String search,
            String departmentFilter,
            String categoryFilter,
            String tenantSlug,
            boolean isScheduled
    ) {
        log.info("Starting asynchronous PDF report compilation for {} on tenant {}", recipientEmail, tenantSlug);
        TenantContext.setTenant(tenantSlug);
        try {
            tenantSchemaService.applyCurrentTenantSearchPath();

            // 1. Resolve administrator name and tenant company name dynamically
            String adminName = userRepository.findByEmail(recipientEmail)
                    .map(User::getName)
                    .orElse("Administrator");

            String tenantName = tenantRepository.findBySlug(tenantSlug)
                    .map(Tenant::getCompanyName)
                    .orElse(tenantSlug);

            // 2. Generate PDF bytes using Playwright and light-theme templates
            byte[] pdfBytes = adminAnalyticsService.getLearningAuditReportPdf(search, departmentFilter, categoryFilter);

            // 3. Render HTML email using our professional Thymeleaf template
            Context thymeleafContext = new Context();
            thymeleafContext.setVariable("adminName", adminName);
            thymeleafContext.setVariable("tenantName", tenantName);
            thymeleafContext.setVariable("search", search);
            thymeleafContext.setVariable("department", departmentFilter);
            thymeleafContext.setVariable("category", categoryFilter);
            thymeleafContext.setVariable("generationDate", LocalDate.now().toString());
            thymeleafContext.setVariable("isScheduled", isScheduled);

            String htmlBody = templateEngine.process("email/Icentric_Email_Learning_Audit", thymeleafContext);

            String subject = "Corporate Learning Audit & Talent Excellence Report - " + LocalDate.now();
            String filename = "Learning_Audit_Report_" + LocalDate.now() + ".pdf";

            // 4. Email PDF attachment asynchronously
            emailService.sendEmailWithAttachment(recipientEmail, subject, htmlBody, pdfBytes, filename).join();
            log.info("Asynchronous PDF report successfully sent to {}", recipientEmail);

        } catch (Exception e) {
            log.error("Failed to generate and email PDF report to {}", recipientEmail, e);
        } finally {
            TenantContext.clear();
        }
    }
}

package com.icentric.Icentric.jobs;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.learning.constants.CertificateStatus;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.scheduler.AssignmentNotificationScheduler;
import com.icentric.Icentric.learning.service.CertificateIssuanceAsyncService;
import com.icentric.Icentric.learning.service.NotificationService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helper component that manages transaction boundaries for background scheduled jobs.
 * Enforces strict transaction and Hibernate First-Level Cache isolation per tenant
 * by running each execution in Propagation.REQUIRES_NEW.
 */
@Component
public class TenantJobHelper {

    private static final Logger log = LoggerFactory.getLogger(TenantJobHelper.class);

    /** Maximum number of automated retry attempts before a certificate is left for manual admin intervention. */
    static final int MAX_RETRY_ATTEMPTS = 3;

    /** How long a certificate must be stuck in PENDING before the scheduler considers it stalled. */
    static final long PENDING_STALE_MINUTES = 30;

    private final NotificationService notificationService;
    private final AssignmentScheduler assignmentScheduler;
    private final AssignmentNotificationScheduler assignmentNotificationScheduler;
    private final TenantSchemaService tenantSchemaService;
    private final EntityManager entityManager;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final CertificateIssuanceAsyncService certificateIssuanceAsyncService;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public TenantJobHelper(
            NotificationService notificationService,
            @org.springframework.context.annotation.Lazy AssignmentScheduler assignmentScheduler,
            @org.springframework.context.annotation.Lazy AssignmentNotificationScheduler assignmentNotificationScheduler,
            TenantSchemaService tenantSchemaService,
            EntityManager entityManager,
            IssuedCertificateRepository issuedCertificateRepository,
            CertificateIssuanceAsyncService certificateIssuanceAsyncService,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.notificationService = notificationService;
        this.assignmentScheduler = assignmentScheduler;
        this.assignmentNotificationScheduler = assignmentNotificationScheduler;
        this.tenantSchemaService = tenantSchemaService;
        this.entityManager = entityManager;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.certificateIssuanceAsyncService = certificateIssuanceAsyncService;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    /**
     * Processes pending notification events for a single tenant inside a separate transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTenantNotifications(Tenant tenant) {
        TenantContext.setTenant(tenant.getSlug());
        entityManager.createNativeQuery(
                "SET LOCAL search_path TO \"tenant_" + tenant.getSlug() + "\""
        ).executeUpdate();
        try {
            notificationService.processNotifications();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Evaluates and marks overdue assignments for a single tenant inside a separate transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTenantOverdueAssignments(Tenant tenant) {
        assignmentScheduler.markOverdueAssignmentsForTenant(tenant.getSlug());
    }

    /**
     * Evaluates and creates reminders/escalations for assignments for a single tenant inside a separate transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTenantAssignments(Tenant tenant, Instant now) {
        assignmentNotificationScheduler.processAssignmentsForTenant(tenant, now);
    }

    /**
     * Scans for certificates stuck in FAILED or stale PENDING state for a single tenant
     * and re-queues their async generation pipeline.
     *
     * <p>A certificate is considered stuck when:
     * <ul>
     *   <li>Its status is {@code FAILED} (generation threw an exception), OR</li>
     *   <li>Its status is {@code PENDING} and {@code issuedAt} is older than
     *       {@value #PENDING_STALE_MINUTES} minutes (async task silently died).</li>
     * </ul>
     *
     * <p>Records that have already been retried {@value #MAX_RETRY_ATTEMPTS} times are
     * skipped and left in FAILED state for manual admin review — they will not be
     * touched by the scheduler again.
     *
     * <p>Runs inside {@code Propagation.REQUIRES_NEW} so each tenant gets its own
     * isolated Hibernate first-level cache, preventing cross-tenant data bleed.
     *
     * @param tenant the tenant to scan
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryStuckCertificatesForTenant(Tenant tenant) {
        String slug = tenant.getSlug();
        TenantContext.setTenant(slug);
        entityManager.createNativeQuery(
                "SET LOCAL search_path TO \"tenant_" + slug + "\""
        ).executeUpdate();

        try {
            Instant pendingThreshold = Instant.now().minus(PENDING_STALE_MINUTES, ChronoUnit.MINUTES);

            // Collect FAILED certs + stale PENDING certs
            List<IssuedCertificate> stuck = new ArrayList<>();
            stuck.addAll(issuedCertificateRepository.findByStatus(CertificateStatus.FAILED));
            stuck.addAll(issuedCertificateRepository.findPendingOlderThan(pendingThreshold));

            if (stuck.isEmpty()) {
                return;
            }

            log.info("[CertificateRetry] tenant={} — found {} stuck certificate(s)", slug, stuck.size());

            for (IssuedCertificate issued : stuck) {
                // Skip records that have exhausted all automated retries
                if (issued.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                    log.warn(
                            "[CertificateRetry] tenant={} cert={} userId={} — skipped, exhausted {} retries. Needs manual admin intervention.",
                            slug, issued.getId(), issued.getUserId(), MAX_RETRY_ATTEMPTS
                    );
                    continue;
                }

                // Reset to PENDING and increment the retry counter
                issued.setStatus(CertificateStatus.PENDING);
                issued.setGenerationError(null);
                issued.setBlobPath(null);
                issued.setFileName(null);
                issued.setGeneratedAt(null);
                issued.setRetryCount(issued.getRetryCount() + 1);
                issuedCertificateRepository.save(issued);

                log.info(
                        "[CertificateRetry] tenant={} cert={} userId={} — queued for retry (attempt {}/{})",
                        slug, issued.getId(), issued.getUserId(), issued.getRetryCount(), MAX_RETRY_ATTEMPTS
                );

                auditService.log(
                        issued.getUserId(),
                        AuditAction.CERTIFICATE_ISSUED,
                        "CERTIFICATE",
                        issued.getId().toString(),
                        "Automated retry scheduler re-queued certificate generation for "
                                + auditMetadataService.describeUserInCurrentTenant(issued.getUserId())
                                + " (attempt " + issued.getRetryCount() + "/" + MAX_RETRY_ATTEMPTS + ")."
                );

                // Register afterCommit to fire the async generation AFTER the TX commits,
                // so the updated PENDING record is visible to the async worker thread.
                final UUID certId = issued.getId();
                final String tenantSlug = slug;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        certificateIssuanceAsyncService.generateAndStore(certId, tenantSlug);
                    }
                });
            }
        } finally {
            TenantContext.clear();
        }
    }
}

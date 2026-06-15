package com.icentric.Icentric.jobs;

import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job that automatically detects and re-queues certificate generation
 * for any {@code IssuedCertificate} records stuck in {@code FAILED} or stale
 * {@code PENDING} state across all tenants.
 *
 * <h3>What counts as "stuck"?</h3>
 * <ul>
 *   <li><b>FAILED</b> — The async Playwright PDF generation threw an exception.</li>
 *   <li><b>Stale PENDING</b> — The record has been in PENDING for longer than
 *       {@link TenantJobHelper#PENDING_STALE_MINUTES} minutes, meaning the async
 *       task silently died before it could update the status to FAILED.</li>
 * </ul>
 *
 * <h3>Retry cap</h3>
 * <p>Each certificate is retried at most {@link TenantJobHelper#MAX_RETRY_ATTEMPTS}
 * times (currently 3). Once exhausted the record is left in {@code FAILED} and a
 * {@code WARN} log is emitted so that an admin can investigate and retry manually
 * via {@code POST /api/v1/admin/certificates/{id}/regenerate}.
 *
 * <h3>Schedule</h3>
 * <p>Runs every 30 minutes ({@code fixedRate = 1_800_000 ms}).
 * The initial 5-minute startup delay ({@code initialDelay = 300_000 ms}) gives the
 * application time to finish booting and warm up its connection pool before the
 * first scan fires.
 *
 * <h3>Transaction isolation</h3>
 * <p>Each tenant is processed inside its own {@code Propagation.REQUIRES_NEW}
 * transaction via {@link TenantJobHelper#retryStuckCertificatesForTenant(Tenant)}.
 * This eliminates any risk of Hibernate first-level cache bleed across tenants and
 * ensures one tenant's failure cannot affect another's.
 */
@Component
public class CertificateRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificateRetryScheduler.class);

    private final TenantRepository tenantRepository;
    private final TenantJobHelper tenantJobHelper;

    public CertificateRetryScheduler(
            TenantRepository tenantRepository,
            TenantJobHelper tenantJobHelper
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantJobHelper = tenantJobHelper;
    }

    /**
     * Main entry point for the certificate retry sweep.
     *
     * <p>Iterates every registered tenant and delegates per-tenant processing
     * to {@link TenantJobHelper#retryStuckCertificatesForTenant(Tenant)}.
     * Failures for one tenant are caught and logged without aborting the sweep
     * for the remaining tenants.
     */
    @Scheduled(fixedRate = 1_800_000, initialDelay = 300_000)
    public void retryStuckCertificates() {
        List<Tenant> tenants = tenantRepository.findAll();

        if (tenants.isEmpty()) {
            return;
        }

        log.debug("[CertificateRetry] Starting sweep across {} tenant(s)", tenants.size());

        int requeued = 0;
        for (Tenant tenant : tenants) {
            try {
                tenantJobHelper.retryStuckCertificatesForTenant(tenant);
                requeued++;
            } catch (Exception e) {
                // Log and continue — one bad tenant must not block the others
                log.error(
                        "[CertificateRetry] Failed to process tenant='{}': {}",
                        tenant.getSlug(), e.getMessage(), e
                );
            }
        }

        log.debug("[CertificateRetry] Sweep complete — processed {}/{} tenant(s)", requeued, tenants.size());
    }
}

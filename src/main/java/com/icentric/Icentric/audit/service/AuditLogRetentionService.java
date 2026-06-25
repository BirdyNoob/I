package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.audit.repository.AuditLogRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuditLogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionService.class);
    private static final int RETENTION_MONTHS = 12;

    private final AuditLogRepository repository;

    public AuditLogRetentionService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 3 1 * *") // 3 AM on the 1st of every month
    @SchedulerLock(name = "auditLogRetention", lockAtLeastFor = "5m", lockAtMostFor = "30m")
    @Transactional
    public void purgeOldLogs() {
        Instant cutoff = Instant.now().minus(RETENTION_MONTHS * 30L, ChronoUnit.DAYS);
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        log.info("Audit log retention: deleted {} records older than {} months", deleted, RETENTION_MONTHS);
    }
}

package com.icentric.Icentric.learning.scheduler;

import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.service.LearningAuditAsyncService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class LearningAuditScheduler {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;
    private final LearningAuditAsyncService learningAuditAsyncService;
    private final TenantSchemaService tenantSchemaService;

    public LearningAuditScheduler(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            UserRepository userRepository,
            LearningAuditAsyncService learningAuditAsyncService,
            TenantSchemaService tenantSchemaService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.userRepository = userRepository;
        this.learningAuditAsyncService = learningAuditAsyncService;
        this.tenantSchemaService = tenantSchemaService;
    }

    /**
     * Automated compliance cron trigger that compiles and emails the Corporate Learning Audit
     * & Talent Excellence Report to all Super Admins across all SaaS tenants.
     * Scheduled to execute every Monday morning at 2:00 AM UTC.
     */
    @Scheduled(cron = "0 0 2 * * MON")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "weeklyLearningAudit", lockAtLeastFor = "10m", lockAtMostFor = "60m")
    public void runWeeklyCorporateAudits() {
        log.info("Starting scheduled weekly Corporate Learning Audit automated compiler runs");
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenant(tenant.getSlug());
                tenantSchemaService.applyCurrentTenantSearchPath();

                // Find Super Admins in current tenant to receive the weekly report
                List<TenantUser> superAdmins = tenantUserRepository.findByTenantId(tenant.getId())
                        .stream()
                        .filter(tu -> "SUPER_ADMIN".equalsIgnoreCase(tu.getRole()))
                        .toList();

                for (TenantUser sa : superAdmins) {
                    userRepository.findById(sa.getUserId()).ifPresent(user -> {
                        if (user.getEmail() != null) {
                            log.info("Scheduling automated weekly learning audit email for Super Admin {} on tenant {}", user.getEmail(), tenant.getSlug());
                            learningAuditAsyncService.compileAndEmailReport(
                                    user.getEmail(),
                                    null, // no search queries
                                    null, // all departments
                                    null, // all categories
                                    tenant.getSlug(),
                                    true
                            );
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Scheduled weekly audit compiling failed on tenant slug {}", tenant.getSlug(), e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Finished automated weekly Corporate Learning Audit scheduling tasks");
    }
}

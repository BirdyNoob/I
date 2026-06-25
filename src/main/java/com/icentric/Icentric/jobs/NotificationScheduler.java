package com.icentric.Icentric.jobs;

import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final TenantRepository tenantRepository;
    private final TenantJobHelper tenantJobHelper;

    public NotificationScheduler(
            TenantRepository tenantRepository,
            TenantJobHelper tenantJobHelper
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantJobHelper = tenantJobHelper;
    }

    @Scheduled(fixedRate = 60000) // every 1 min
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "sendNotifications", lockAtLeastFor = "30s", lockAtMostFor = "5m")
    public void sendNotifications() {
        var tenants = tenantRepository.findAll();

        for (var tenant : tenants) {
            try {
                tenantJobHelper.processTenantNotifications(tenant);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(NotificationScheduler.class)
                        .error("Failed to process notifications for tenant: {}", tenant.getSlug(), e);
            }
        }
    }
}

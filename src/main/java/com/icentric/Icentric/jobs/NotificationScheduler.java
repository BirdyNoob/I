package com.icentric.Icentric.jobs;

import com.icentric.Icentric.learning.service.NotificationService;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final TenantRepository tenantRepository;
    private final EntityManager entityManager;

    public NotificationScheduler(
            NotificationService notificationService,
            TenantRepository tenantRepository,
            EntityManager entityManager
    ) {
        this.notificationService = notificationService;
        this.tenantRepository = tenantRepository;
        this.entityManager = entityManager;
    }

    @Scheduled(fixedRate = 60000) // every 1 min
    @Transactional
    public void sendNotifications() {
        var tenants = tenantRepository.findAll();

        for (var tenant : tenants) {
            TenantContext.setTenant(tenant.getSlug());

            try {
                entityManager.createNativeQuery(
                        "SET LOCAL search_path TO tenant_" + tenant.getSlug()
                ).executeUpdate();

                notificationService.processNotifications();
            } finally {
                TenantContext.clear();
            }
        }
    }
}

package com.icentric.Icentric.jobs;

import com.icentric.Icentric.learning.service.NotificationService;
import com.icentric.Icentric.learning.scheduler.AssignmentNotificationScheduler;
import com.icentric.Icentric.jobs.AssignmentScheduler;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Helper component that manages transaction boundaries for background scheduled jobs.
 * Enforces strict transaction and Hibernate First-Level Cache isolation per tenant
 * by running each execution in Propagation.REQUIRES_NEW.
 */
@Component
public class TenantJobHelper {

    private final NotificationService notificationService;
    private final AssignmentScheduler assignmentScheduler;
    private final AssignmentNotificationScheduler assignmentNotificationScheduler;
    private final TenantSchemaService tenantSchemaService;
    private final EntityManager entityManager;

    public TenantJobHelper(
            NotificationService notificationService,
            @org.springframework.context.annotation.Lazy AssignmentScheduler assignmentScheduler,
            @org.springframework.context.annotation.Lazy AssignmentNotificationScheduler assignmentNotificationScheduler,
            TenantSchemaService tenantSchemaService,
            EntityManager entityManager
    ) {
        this.notificationService = notificationService;
        this.assignmentScheduler = assignmentScheduler;
        this.assignmentNotificationScheduler = assignmentNotificationScheduler;
        this.tenantSchemaService = tenantSchemaService;
        this.entityManager = entityManager;
    }

    /**
     * Processes pending notification events for a single tenant inside a separate transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTenantNotifications(Tenant tenant) {
        TenantContext.setTenant(tenant.getSlug());
        entityManager.createNativeQuery(
                "SET LOCAL search_path TO tenant_" + tenant.getSlug()
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
}

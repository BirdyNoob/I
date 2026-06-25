package com.icentric.Icentric.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.entity.TenantStatsCache;
import com.icentric.Icentric.platform.repository.TenantStatsCacheRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TenantStatsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(TenantStatsAggregationService.class);

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final TenantStatsCacheRepository cacheRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public TenantStatsAggregationService(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            UserAssignmentRepository userAssignmentRepository,
            IssuedCertificateRepository issuedCertificateRepository,
            TenantStatsCacheRepository cacheRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.userAssignmentRepository = userAssignmentRepository;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.cacheRepository = cacheRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @SchedulerLock(name = "aggregateTenantStats", lockAtLeastFor = "2m", lockAtMostFor = "10m")
    @Transactional
    public void aggregateStats() {
        var tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            try {
                aggregateForTenant(tenant);
            } catch (Exception e) {
                log.error("Failed to aggregate stats for tenant: {}", tenant.getSlug(), e);
            }
        }
    }

    private void aggregateForTenant(Tenant tenant) {
        String slug = tenant.getSlug();

        // Switch schema
        entityManager.createNativeQuery("SET LOCAL search_path TO \"tenant_" + slug + "\"").executeUpdate();

        long totalAssignments = userAssignmentRepository.count();
        long completed = userAssignmentRepository.countByStatus(AssignmentStatus.COMPLETED);
        long overdue = userAssignmentRepository.countByStatus(AssignmentStatus.OVERDUE);
        long certs = issuedCertificateRepository.count();
        int percent = totalAssignments > 0 ? (int) Math.round(100.0 * completed / totalAssignments) : 0;

        // Department completion
        Map<String, Integer> deptCompletion = userAssignmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        ua -> "General",
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            long c = list.stream().filter(ua -> ua.getStatus() == AssignmentStatus.COMPLETED).count();
                            return list.isEmpty() ? 0 : (int) Math.round(100.0 * c / list.size());
                        })
                ));

        // Reset schema
        entityManager.createNativeQuery("SET LOCAL search_path TO system").executeUpdate();

        long users = tenantUserRepository.countByTenantId(tenant.getId());

        // Upsert cache
        TenantStatsCache cache = cacheRepository.findById(slug).orElse(new TenantStatsCache());
        cache.setTenantSlug(slug);
        cache.setTotalUsers(users);
        cache.setTotalAssignments(totalAssignments);
        cache.setCompletedAssignments(completed);
        cache.setOverdueAssignments(overdue);
        cache.setCompletionPercent(percent);
        cache.setCertsIssued(certs);
        cache.setUpdatedAt(Instant.now());
        try {
            cache.setDeptCompletion(objectMapper.writeValueAsString(deptCompletion));
        } catch (Exception e) {
            cache.setDeptCompletion("{}");
        }
        cacheRepository.save(cache);
    }
}

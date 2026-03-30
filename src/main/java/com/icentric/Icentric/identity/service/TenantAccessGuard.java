package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantAccessGuard {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;

    public TenantAccessGuard(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
    }

    public Tenant currentTenant() {
        String slug = currentTenantSlug();
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));
    }

    public UUID currentTenantId() {
        return currentTenant().getId();
    }

    public String currentTenantSlug() {
        String slug = TenantContext.getTenant();
        if (slug == null || slug.isBlank()) {
            throw new IllegalStateException("Missing tenant in request context");
        }
        return slug;
    }

    public boolean isSystemTenant() {
        return "system".equals(currentTenantSlug());
    }

    public void assertUserBelongsToCurrentTenant(UUID userId) {
        if (isSystemTenant()) {
            return;
        }
        UUID tenantId = currentTenantId();
        if (!tenantUserRepository.existsByUserIdAndTenantId(userId, tenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant: " + userId);
        }
    }

    public void assertUsersBelongToCurrentTenant(Collection<UUID> userIds) {
        if (isSystemTenant() || userIds == null || userIds.isEmpty()) {
            return;
        }

        Set<UUID> distinctUserIds = new LinkedHashSet<>(userIds);
        UUID tenantId = currentTenantId();
        long allowedCount = tenantUserRepository.countByTenantIdAndUserIdIn(tenantId, distinctUserIds);
        if (allowedCount == distinctUserIds.size()) {
            return;
        }

        List<UUID> allowedUserIds = tenantUserRepository.findUserIdsByTenantIdAndUserIdIn(tenantId, distinctUserIds);
        Set<UUID> allowedSet = new LinkedHashSet<>(allowedUserIds);
        List<UUID> forbiddenIds = distinctUserIds.stream()
                .filter(userId -> !allowedSet.contains(userId))
                .toList();
        throw new AccessDeniedException("Users do not belong to the current tenant: " + forbiddenIds);
    }
}

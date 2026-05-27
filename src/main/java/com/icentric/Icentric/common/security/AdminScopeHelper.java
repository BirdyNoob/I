package com.icentric.Icentric.common.security;

import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Encapsulates the "standard-admin scoping" logic that was previously duplicated
 * verbatim in {@code AuditService}, {@code CertificateService} (×2), and elsewhere.
 *
 * <p>A "standard admin" is an ADMIN-role {@link TenantUser} who was NOT a
 * super-admin (PLATFORM_ADMIN). They may only see data for themselves and the
 * learners they directly onboarded (i.e. created).</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * AdminScopeHelper.AdminScope scope = adminScopeHelper.resolveForCurrentUser();
 * if (scope.isStandardAdmin()) {
 *     list.removeIf(item -> !scope.isInScope(item.getUserId()));
 * }
 * }</pre>
 */
@Component
public class AdminScopeHelper {

    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;

    public AdminScopeHelper(TenantUserRepository tenantUserRepository,
                            TenantRepository tenantRepository) {
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Resolves the admin scoping context for the currently authenticated user.
     *
     * @return an {@link AdminScope} that carries the role flag and scoped user list
     */
    public AdminScope resolveForCurrentUser() {
        UUID authUserId = SecurityUtils.currentUserIdOrNull();
        String tenant = TenantContext.getTenant();

        if (authUserId == null || tenant == null) {
            return AdminScope.noScope();
        }

        Tenant tenantEntity = tenantRepository.findBySlug(tenant).orElse(null);
        if (tenantEntity == null) {
            return AdminScope.noScope();
        }

        TenantUser membership = tenantUserRepository
                .findByUserIdAndTenantId(authUserId, tenantEntity.getId())
                .orElse(null);

        if (membership == null || !"ADMIN".equals(membership.getRole())) {
            return AdminScope.noScope();
        }

        List<UUID> onboarded = tenantUserRepository
                .findUserIdsByTenantIdAndCreatedBy(tenantEntity.getId(), authUserId);

        return new AdminScope(true, authUserId, onboarded != null ? onboarded : List.of());
    }

    // ── Inner value-object ────────────────────────────────────────────────────

    /**
     * Immutable result of a scope resolution.
     */
    public static final class AdminScope {

        private final boolean standardAdmin;
        private final UUID adminUserId;
        private final List<UUID> onboardedUserIds;

        private AdminScope(boolean standardAdmin, UUID adminUserId, List<UUID> onboardedUserIds) {
            this.standardAdmin = standardAdmin;
            this.adminUserId = adminUserId;
            this.onboardedUserIds = Collections.unmodifiableList(onboardedUserIds);
        }

        static AdminScope noScope() {
            return new AdminScope(false, null, List.of());
        }

        /** {@code true} if the current user is a standard (non-super) admin. */
        public boolean isStandardAdmin() {
            return standardAdmin;
        }

        /** The admin's own user ID. */
        public UUID getAdminUserId() {
            return adminUserId;
        }

        /** IDs of users that this admin onboarded. Does not include the admin themselves. */
        public List<UUID> getOnboardedUserIds() {
            return onboardedUserIds;
        }

        /**
         * Returns {@code true} if the given user ID belongs to the admin themselves
         * or one of the users they onboarded.
         */
        public boolean isInScope(UUID userId) {
            if (userId == null) return false;
            return userId.equals(adminUserId) || onboardedUserIds.contains(userId);
        }
    }
}

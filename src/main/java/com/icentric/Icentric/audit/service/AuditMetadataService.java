package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuditMetadataService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final TrackRepository trackRepository;

    public AuditMetadataService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            TrackRepository trackRepository
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.trackRepository = trackRepository;
    }

    public String describeUser(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "user " + userId;
        }

        StringBuilder value = new StringBuilder();
        if (user.getName() != null && !user.getName().isBlank()) {
            value.append(user.getName());
        } else {
            value.append("User");
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            value.append(" <").append(user.getEmail()).append(">");
        }
        value.append(" [").append(user.getId()).append("]");
        return value.toString();
    }

    public String describeUserInCurrentTenant(UUID userId) {
        String base = describeUser(userId);
        Optional<TenantUser> membership = currentTenantMembership(userId);
        if (membership.isEmpty()) {
            return base;
        }

        TenantUser tenantUser = membership.get();
        StringBuilder value = new StringBuilder(base);
        if (tenantUser.getDepartment() != null) {
            value.append(", department=").append(tenantUser.getDepartment().getDisplayName());
        }
        if (tenantUser.getRole() != null && !tenantUser.getRole().isBlank()) {
            value.append(", role=").append(tenantUser.getRole());
        }
        return value.toString();
    }

    public String describeTrack(UUID trackId) {
        Track track = trackRepository.findById(trackId).orElse(null);
        if (track == null) {
            return "track " + trackId;
        }

        String title = track.getTitle() != null && !track.getTitle().isBlank()
                ? track.getTitle()
                : "Track";
        return title + " [" + track.getId() + "]";
    }

    public String currentTenantLabel() {
        String slug = TenantContext.getTenant();
        if (slug == null || slug.isBlank()) {
            return "tenant=unknown";
        }

        Tenant tenant = tenantRepository.findBySlug(slug).orElse(null);
        if (tenant == null) {
            return "tenant=" + slug;
        }
        return "tenant=" + tenant.getSlug() + " [" + tenant.getId() + "]";
    }

    private Optional<TenantUser> currentTenantMembership(UUID userId) {
        String slug = TenantContext.getTenant();
        if (slug == null || slug.isBlank() || "system".equals(slug)) {
            return Optional.empty();
        }

        return tenantRepository.findBySlug(slug)
                .flatMap(tenant -> tenantUserRepository.findByUserIdAndTenantId(userId, tenant.getId()));
    }
}

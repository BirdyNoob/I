package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.dto.AuditLogResponse;
import com.icentric.Icentric.audit.entity.AuditLog;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import jakarta.persistence.criteria.Predicate;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final TrackRepository trackRepository;
    private final CertificateRepository certificateRepository;

    public AuditService(
            AuditLogRepository repository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            TrackRepository trackRepository,
            CertificateRepository certificateRepository
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.trackRepository = trackRepository;
        this.certificateRepository = certificateRepository;
    }

    public Page<AuditLogResponse> getLogs(
            Pageable pageable,
            AuditAction action,
            String entityType,
            UUID userId,
            Instant createdFrom,
            Instant createdTo
    ) {
        String tenant = TenantContext.getTenant();
        Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!"system".equals(tenant)) {
                predicates.add(criteriaBuilder.equal(root.get("tenantSlug"), tenant));
            }
            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), entityType));
            }
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        Page<AuditLog> page = repository.findAll(spec, pageable);
        List<AuditLog> logs = page.getContent();
        if (logs.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        Map<UUID, User> usersById = userRepository.findByIdIn(
                        logs.stream()
                                .map(AuditLog::getUserId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));

        Map<String, Tenant> tenantsBySlug = tenantRepository.findBySlugIn(
                        logs.stream()
                                .map(AuditLog::getTenantSlug)
                                .filter(slug -> slug != null && !slug.isBlank())
                                .distinct()
                                .toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(Tenant::getSlug, tenantEntity -> tenantEntity));

        Map<String, TenantUser> membershipsByTenantAndUser = resolveMemberships(logs, tenantsBySlug);
        Map<String, String> entityDisplayNames = resolveEntityDisplayNames(logs, usersById);

        List<AuditLogResponse> responses = logs.stream()
                .map(log -> toResponse(
                        log,
                        usersById.get(log.getUserId()),
                        membershipsByTenantAndUser.get(membershipKey(log.getTenantSlug(), log.getUserId())),
                        tenantsBySlug.get(log.getTenantSlug()),
                        entityDisplayNames.get(entityKey(log.getEntityType(), log.getEntityId()))
                ))
                .toList();

        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    public void log(
            UUID userId,
            AuditAction action,
            String entityType,
            String entityId,
            String details
    ) {
        logForTenant(userId, action, entityType, entityId, details, TenantContext.getTenant());
    }

    public void logForTenant(
            UUID userId,
            AuditAction action,
            String entityType,
            String entityId,
            String details,
            String tenantSlug
    ) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setUserId(userId);
        log.setTenantSlug(tenantSlug);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setCreatedAt(Instant.now());

        repository.save(log);
    }

    private Map<String, TenantUser> resolveMemberships(List<AuditLog> logs, Map<String, Tenant> tenantsBySlug) {
        Map<String, TenantUser> memberships = new HashMap<>();

        Map<String, Set<UUID>> userIdsByTenantSlug = new HashMap<>();
        for (AuditLog log : logs) {
            if (log.getUserId() == null || log.getTenantSlug() == null || log.getTenantSlug().isBlank()
                    || "system".equals(log.getTenantSlug())) {
                continue;
            }
            userIdsByTenantSlug
                    .computeIfAbsent(log.getTenantSlug(), ignored -> new java.util.LinkedHashSet<>())
                    .add(log.getUserId());
        }

        userIdsByTenantSlug.forEach((tenantSlug, userIds) -> {
            Tenant tenant = tenantsBySlug.get(tenantSlug);
            if (tenant == null || userIds.isEmpty()) {
                return;
            }
            tenantUserRepository.findByTenantIdAndUserIdIn(tenant.getId(), userIds)
                    .forEach(membership -> memberships.put(
                            membershipKey(tenantSlug, membership.getUserId()),
                            membership
                    ));
        });

        return memberships;
    }

    private Map<String, String> resolveEntityDisplayNames(List<AuditLog> logs, Map<UUID, User> usersById) {
        Map<String, String> entityDisplayNames = new HashMap<>();

        List<UUID> trackIds = parseEntityIds(logs, "TRACK");
        trackRepository.findAllById(trackIds)
                .forEach(track -> entityDisplayNames.put(
                        entityKey("TRACK", track.getId().toString()),
                        track.getTitle()
                ));

        List<UUID> userIds = parseEntityIds(logs, "USER");
        userRepository.findByIdIn(userIds)
                .forEach(user -> entityDisplayNames.put(
                        entityKey("USER", user.getId().toString()),
                        userDisplayName(user)
                ));

        List<UUID> certificateIds = parseEntityIds(logs, "CERTIFICATE");
        certificateRepository.findAllById(certificateIds)
                .forEach(certificate -> entityDisplayNames.put(
                        entityKey("CERTIFICATE", certificate.getId().toString()),
                        certificate.getTitle()
                ));

        return entityDisplayNames;
    }

    private List<UUID> parseEntityIds(List<AuditLog> logs, String entityType) {
        return logs.stream()
                .filter(log -> entityType.equalsIgnoreCase(log.getEntityType()))
                .map(AuditLog::getEntityId)
                .filter(id -> id != null && !id.isBlank())
                .map(this::parseUuid)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AuditLogResponse toResponse(
            AuditLog log,
            User actor,
            TenantUser membership,
            Tenant tenant,
            String entityDisplayName
    ) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                actionLabel(log.getAction()),
                log.getEntityType(),
                log.getEntityId(),
                entityDisplayName != null ? entityDisplayName : fallbackEntityDisplayName(log),
                log.getDetails(),
                log.getCreatedAt(),
                log.getUserId(),
                actor != null ? actor.getName() : null,
                actor != null ? actor.getEmail() : null,
                membership != null ? membership.getDepartment() : null,
                membership != null ? membership.getRole() : null,
                log.getTenantSlug(),
                tenant != null ? tenant.getCompanyName() : null
        );
    }

    private String fallbackEntityDisplayName(AuditLog log) {
        if (log.getEntityId() == null || log.getEntityId().isBlank()) {
            return null;
        }
        return switch (log.getEntityType() == null ? "" : log.getEntityType().toUpperCase(Locale.ROOT)) {
            case "TRACK" -> "Track " + log.getEntityId();
            case "USER" -> "User " + log.getEntityId();
            case "CERTIFICATE" -> "Certificate " + log.getEntityId();
            case "ASSIGNMENT" -> "Assignment " + log.getEntityId();
            default -> log.getEntityId();
        };
    }

    private String actionLabel(AuditAction action) {
        if (action == null) {
            return null;
        }
        String normalized = action.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = normalized.split(" ");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return label.toString();
    }

    private String userDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    private String membershipKey(String tenantSlug, UUID userId) {
        return tenantSlug + ":" + userId;
    }

    private String entityKey(String entityType, String entityId) {
        return (entityType == null ? "" : entityType.toUpperCase(Locale.ROOT)) + ":" + entityId;
    }
}

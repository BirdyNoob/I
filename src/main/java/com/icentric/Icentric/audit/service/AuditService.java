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
import com.icentric.Icentric.common.security.AdminScopeHelper;
import com.icentric.Icentric.learning.service.PlaywrightPdfService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final AdminScopeHelper adminScopeHelper;
    private final PlaywrightPdfService playwrightPdfService;

    public AuditService(
            AuditLogRepository repository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            TrackRepository trackRepository,
            CertificateRepository certificateRepository,
            AdminScopeHelper adminScopeHelper,
            PlaywrightPdfService playwrightPdfService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.trackRepository = trackRepository;
        this.certificateRepository = certificateRepository;
        this.adminScopeHelper = adminScopeHelper;
        this.playwrightPdfService = playwrightPdfService;
    }

    public Page<AuditLogResponse> getLogs(
            Pageable pageable,
            AuditAction action,
            String entityType,
            UUID userId,
            Instant createdFrom,
            Instant createdTo
    ) {
        // Resolve admin scope — standard admins may only see logs for themselves and
        // the learners they onboarded.
        AdminScopeHelper.AdminScope scope = adminScopeHelper.resolveForCurrentUser();
        final String tenant = TenantContext.getTenant();

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

            // If standard manager, restrict logs to themselves or users they onboarded
            if (scope.isStandardAdmin() && scope.getAdminUserId() != null) {
                List<UUID> scopedUserIds = new ArrayList<>();
                scopedUserIds.add(scope.getAdminUserId());
                scopedUserIds.addAll(scope.getOnboardedUserIds());

                List<String> scopedUserIdStrings = scopedUserIds.stream().map(UUID::toString).toList();

                Predicate actorPredicate = root.get("userId").in(scopedUserIds);
                Predicate targetPredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(criteriaBuilder.upper(root.get("entityType")), "USER"),
                        root.get("entityId").in(scopedUserIdStrings)
                );

                predicates.add(criteriaBuilder.or(actorPredicate, targetPredicate));
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
                .collect(java.util.stream.Collectors.toMap(Tenant::getSlug, t -> t));

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
                membership != null && membership.getDepartment() != null ? membership.getDepartment().getDisplayName() : null,
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

    /**
     * Compiles a memory-safe, Excel-friendly UTF-8 BOM CSV containing all audit logs matching the filters.
     */
    public String getAuditLogsCsv(AuditAction action, String entityType, UUID userId, Instant createdFrom, Instant createdTo) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // UTF-8 BOM for Microsoft Excel
        csv.append("Timestamp (UTC),Action Type,Target Entity,Actor Name,Actor Email,Department,Role,Tenant Slug,Details\n");

        int page = 0;
        int size = 500;
        boolean hasMore = true;

        while (hasMore) {
            Page<AuditLogResponse> logsPage = getLogs(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                action, entityType, userId, createdFrom, createdTo
            );

            for (AuditLogResponse log : logsPage.getContent()) {
                csv.append(log.createdAt() != null ? log.createdAt().toString() : "").append(",")
                   .append(escapeCsv(log.actionLabel())).append(",")
                   .append(escapeCsv(log.entityDisplayName())).append(",")
                   .append(escapeCsv(log.actorName())).append(",")
                   .append(escapeCsv(log.actorEmail())).append(",")
                   .append(escapeCsv(log.actorDepartment())).append(",")
                   .append(escapeCsv(log.actorRole())).append(",")
                   .append(escapeCsv(log.tenantSlug())).append(",")
                   .append(escapeCsv(log.summary())).append("\n");
            }

            hasMore = logsPage.hasNext();
            page++;
        }
        return csv.toString();
    }

    /**
     * Compiles a premium landscape A4 PDF report of audit logs using Playwright.
     */
    public byte[] getAuditLogsReportPdf(AuditAction action, String entityType, UUID userId, Instant createdFrom, Instant createdTo) {
        // Fetch up to 10,000 logs matching the scoped query context
        Page<AuditLogResponse> logsPage = getLogs(
            PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createdAt")),
            action, entityType, userId, createdFrom, createdTo
        );
        List<AuditLogResponse> logs = logsPage.getContent();

        long totalEvents = logs.size();
        long securityEvents = 0;
        long milestoneEvents = 0;
        long alertEvents = 0;

        for (AuditLogResponse log : logs) {
            AuditAction act = log.action();
            if (act == null) continue;

            String name = act.name();
            if (name.contains("LOGIN") || name.contains("USER") || name.contains("GROUP") || name.contains("IMPERSONATION")) {
                securityEvents++;
            } else if (name.contains("COMPLETED") || name.contains("PASSED") || name.contains("ISSUED") || name.contains("READY")) {
                milestoneEvents++;
            } else if (name.contains("OVERDUE") || name.contains("ESCALATION") || name.contains("LOCKED") || name.contains("FAILED") || name.contains("RATE_LIMITED")) {
                alertEvents++;
            }
        }

        String tenantName = TenantContext.getTenant();
        Tenant tenantEntity = tenantRepository.findBySlug(tenantName).orElse(null);
        String companyName = tenantEntity != null ? tenantEntity.getCompanyName() : tenantName;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<style>\n")
            .append("  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Outfit:wght@500;600;700&display=swap');\n")
            .append("  body {\n")
            .append("    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n")
            .append("    background-color: #ffffff;\n")
            .append("    color: #1f2937;\n")
            .append("    margin: 0;\n")
            .append("    padding: 30px;\n")
            .append("    -webkit-print-color-adjust: exact;\n")
            .append("  }\n")
            .append("  .header {\n")
            .append("    margin-bottom: 25px;\n")
            .append("    border-bottom: 2px solid rgba(79, 70, 229, 0.15);\n")
            .append("    padding-bottom: 15px;\n")
            .append("    display: flex;\n")
            .append("    justify-content: space-between;\n")
            .append("    align-items: flex-end;\n")
            .append("  }\n")
            .append("  .title {\n")
            .append("    font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n")
            .append("    font-size: 24px;\n")
            .append("    font-weight: 700;\n")
            .append("    color: #4f46e5;\n")
            .append("    margin: 0 0 5px 0;\n")
            .append("  }\n")
            .append("  .subtitle {\n")
            .append("    font-size: 13px;\n")
            .append("    color: #4b5563;\n")
            .append("    margin: 0;\n")
            .append("  }\n")
            .append("  .stats-grid {\n")
            .append("    display: flex;\n")
            .append("    gap: 15px;\n")
            .append("    margin-bottom: 25px;\n")
            .append("  }\n")
            .append("  .stat-card {\n")
            .append("    flex: 1;\n")
            .append("    background: #f9fafb;\n")
            .append("    border: 1px solid #e5e7eb;\n")
            .append("    padding: 15px;\n")
            .append("    border-radius: 8px;\n")
            .append("    text-align: center;\n")
            .append("  }\n")
            .append("  .stat-val {\n")
            .append("    font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n")
            .append("    font-size: 22px;\n")
            .append("    font-weight: 700;\n")
            .append("    color: #4f46e5;\n")
            .append("  }\n")
            .append("  .stat-lbl {\n")
            .append("    font-size: 11px;\n")
            .append("    color: #6b7280;\n")
            .append("    margin-top: 3px;\n")
            .append("  }\n")
            .append("  table {\n")
            .append("    width: 100%;\n")
            .append("    border-collapse: collapse;\n")
            .append("    margin-bottom: 25px;\n")
            .append("    background: #ffffff;\n")
            .append("    border-radius: 8px;\n")
            .append("    overflow: hidden;\n")
            .append("    border: 1px solid #e5e7eb;\n")
            .append("  }\n")
            .append("  th, td {\n")
            .append("    padding: 10px 12px;\n")
            .append("    text-align: left;\n")
            .append("    font-size: 11px;\n")
            .append("    border-bottom: 1px solid #e5e7eb;\n")
            .append("  }\n")
            .append("  th {\n")
            .append("    background-color: rgba(79, 70, 229, 0.05);\n")
            .append("    font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n")
            .append("    color: #4f46e5;\n")
            .append("    font-weight: 600;\n")
            .append("  }\n")
            .append("  .badge {\n")
            .append("    padding: 2px 6px;\n")
            .append("    border-radius: 4px;\n")
            .append("    font-size: 9px;\n")
            .append("    font-weight: 600;\n")
            .append("    display: inline-block;\n")
            .append("  }\n")
            .append("  .badge-security { background: #fef2f2; color: #dc2626; border: 1px solid #fca5a5; }\n")
            .append("  .badge-milestone { background: #ecfeff; color: #0891b2; border: 1px solid #c5f6fa; }\n")
            .append("  .badge-alert { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }\n")
            .append("  .badge-info { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }\n")
            .append("</style>\n</head>\n<body>\n")
            .append("  <div class=\"header\">\n")
            .append("    <div class=\"header-left\">\n")
            .append("      <h1 class=\"title\">System Audit Logs & Security Activity Report</h1>\n")
            .append("      <p class=\"subtitle\">Headless Playwright PDF Export | Tenant: ").append(escapeHtml(companyName)).append("</p>\n")
            .append("    </div>\n")
            .append("    <div class=\"header-right\">\n")
            .append("      Date Compiled: ").append(java.time.LocalDate.now().toString()).append("<br>\n")
            .append("      Security Level: Scoped Tenant Context\n")
            .append("    </div>\n")
            .append("  </div>\n")
            .append("  <div class=\"stats-grid\">\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(totalEvents).append("</div>\n")
            .append("      <div class=\"stat-lbl\">TOTAL EVENTS</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(securityEvents).append("</div>\n")
            .append("      <div class=\"stat-lbl\">🛡️ SECURITY & ACCESS</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(milestoneEvents).append("</div>\n")
            .append("      <div class=\"stat-lbl\">🎓 LEARNER MILESTONES</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(alertEvents).append("</div>\n")
            .append("      <div class=\"stat-lbl\">⚠️ WARNINGS & ALERTS</div>\n")
            .append("    </div>\n")
            .append("  </div>\n")
            .append("  <table>\n")
            .append("    <thead>\n")
            .append("      <tr>\n")
            .append("        <th>Timestamp (UTC)</th>\n")
            .append("        <th>Action Type</th>\n")
            .append("        <th>Target Entity</th>\n")
            .append("        <th>Actor</th>\n")
            .append("        <th>Details</th>\n")
            .append("      </tr>\n")
            .append("    </thead>\n")
            .append("    <tbody>\n");

        for (AuditLogResponse log : logs) {
            AuditAction act = log.action();
            String badgeClass = "badge-info";
            if (act != null) {
                String name = act.name();
                if (name.contains("LOGIN") || name.contains("USER") || name.contains("GROUP") || name.contains("IMPERSONATION")) {
                    badgeClass = "badge-security";
                } else if (name.contains("COMPLETED") || name.contains("PASSED") || name.contains("ISSUED") || name.contains("READY")) {
                    badgeClass = "badge-milestone";
                } else if (name.contains("OVERDUE") || name.contains("ESCALATION") || name.contains("LOCKED") || name.contains("FAILED") || name.contains("RATE_LIMITED")) {
                    badgeClass = "badge-alert";
                }
            }

            String targetDisplayName = log.entityDisplayName() != null ? log.entityDisplayName() : log.entityId();

            html.append("      <tr>\n")
                .append("        <td>").append(log.createdAt() != null ? log.createdAt().toString() : "").append("</td>\n")
                .append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(escapeHtml(log.actionLabel())).append("</span></td>\n")
                .append("        <td><strong>").append(escapeHtml(log.entityType())).append("</strong><br><span style=\"color:#6b7280;font-size:9px;\">").append(escapeHtml(targetDisplayName)).append("</span></td>\n")
                .append("        <td><strong>").append(escapeHtml(log.actorName())).append("</strong><br><span style=\"color:#9ca3af;font-size:9px;\">").append(escapeHtml(log.actorEmail())).append("</span></td>\n")
                .append("        <td>").append(escapeHtml(log.summary())).append("</td>\n")
                .append("      </tr>\n");
        }

        if (logs.isEmpty()) {
            html.append("      <tr><td colspan=\"5\" style=\"text-align:center;color:#9ca3af;\">No audit logs found matching selected parameters.</td></tr>\n");
        }

        html.append("    </tbody>\n  </table>\n</body>\n</html>");

        return playwrightPdfService.render(html.toString(), true); // landscape A4 format
    }

    private String escapeCsv(String val) {
        if (val == null) {
            return "";
        }
        String escaped = val.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String escapeHtml(String val) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(val == null ? "" : val);
    }

    /**
     * Resolves the email address of the currently authenticated actor.
     */
    public String currentActorUserEmail() {
        UUID actorId = com.icentric.Icentric.common.security.SecurityUtils.currentUserIdOrNull();
        if (actorId == null) {
            return "system@icentric.com";
        }
        return userRepository.findById(actorId)
                .map(User::getEmail)
                .orElse("system@icentric.com");
    }
}

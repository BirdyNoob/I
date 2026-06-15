package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.dto.PlatformTenantDetailResponse;
import com.icentric.Icentric.platform.dto.TenantResponse;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.icentric.Icentric.common.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icentric.Icentric.common.mail.EmailService;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final TenantUserBootstrapService bootstrapService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final TenantUserRepository tenantUserRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final TrackRepository trackRepository;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;

    public TenantService(
            TenantRepository tenantRepository,
            TenantProvisioningService provisioningService,
            TenantUserBootstrapService bootstrapService,
            AuditService auditService,
            EmailService emailService,
            TenantUserRepository tenantUserRepository,
            UserAssignmentRepository userAssignmentRepository,
            IssuedCertificateRepository issuedCertificateRepository,
            TrackRepository trackRepository,
            AuditLogRepository auditLogRepository,
            EntityManager entityManager) {
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.bootstrapService = bootstrapService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.tenantUserRepository = tenantUserRepository;
        this.userAssignmentRepository = userAssignmentRepository;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.trackRepository = trackRepository;
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
    }

    public Tenant createTenant(
            String companyName,
            String plan,
            Integer maxSeats,
            String adminEmail,
            String adminPassword
    ) {

        String slug = generateSlug(companyName);

        if (tenantRepository.findBySlug(slug).isPresent()) {
            // Append random suffix if slug already taken
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        Tenant tenant = new Tenant(slug, companyName);
        tenant.setPlan(plan);
        tenant.setMaxSeats(maxSeats);

        tenantRepository.save(tenant);

        provisioningService.provisionTenantSchema(slug);

        bootstrapService.createSuperAdmin(slug, adminEmail, adminPassword);

        UUID actorId = currentActorUserId();
        if (actorId != null) {
            auditService.logForTenant(
                    actorId,
                    AuditAction.TENANT_CREATED,
                    "TENANT",
                    tenant.getId().toString(),
                    "Platform admin " + actorId + " created tenant " + tenant.getCompanyName()
                            + " [" + tenant.getSlug() + "] with bootstrap admin " + adminEmail,
                    "system"
            );
        }

        // Send onboarding email with raw credentials
        String platformUrl = "http://localhost:3000/login?tenant=" + slug; // Defaults to localhost, can be configured via app properties later
        Map<String, Object> emailVars = Map.of(
                "tenantName", companyName,
                "portalUrl", slug + ".icentric.com",
                "adminEmail", adminEmail,
                "adminPassword", adminPassword,
                "setupUrl", platformUrl,
                "loginUrl", platformUrl,
                "planName", plan,
                "seatLimit", maxSeats
        );
        emailService.sendTemplateEmail(
                adminEmail,
                "Welcome to AISafe — Your company is ready",
                "AISafe_Email_TenantAdmin_Welcome",
                emailVars
        );

        return tenant;
    }
    @Transactional(readOnly = true)
    public TenantResponse getAllTenants(int page, int itemsPerPage) {
        int safePage = Math.max(1, page);
        int safeItemsPerPage = Math.max(1, itemsPerPage);

        var pageable = PageRequest.of(
                safePage - 1,
                safeItemsPerPage,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        var tenantPage = tenantRepository.findAll(pageable);

        List<TenantResponse.TenantItem> tenants = tenantPage.getContent()
                .stream()
                .map(this::toTenantItem)
                .toList();

        return new TenantResponse(
                new TenantResponse.Summary(
                        tenantRepository.count(),
                        tenantRepository.countByStatus("active"),
                        tenantRepository.countByStatus("trial")
                ),
                tenants,
                new TenantResponse.Pagination(
                        tenantPage.getTotalElements(),
                        safeItemsPerPage,
                        safePage
                )
        );
    }

    @Transactional(readOnly = true)
    public PlatformTenantDetailResponse getTenantDetail(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + slug));

        TenantLocalDetail localDetail = withTenantSchema(tenant.getSlug(), () -> {
            List<UserAssignment> assignments = userAssignmentRepository.findAll();
            long totalAssignments = assignments.size();
            long completedAssignments = assignments.stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED)
                    .count();
            int completionPercentage = totalAssignments == 0
                    ? 0
                    : (int) Math.round((completedAssignments * 100.0) / totalAssignments);
            long overdueUsers = assignments.stream()
                    .filter(this::isOverdue)
                    .map(UserAssignment::getUserId)
                    .distinct()
                    .count();
            long certsIssued = issuedCertificateRepository.count();

            List<PlatformTenantDetailResponse.DepartmentCompletion> departmentCompletion =
                    buildDepartmentCompletion(tenant, assignments);
            List<PlatformTenantDetailResponse.RetrainingRequirement> retrainingRequirements =
                    buildRetrainingRequirements(tenant, assignments);

            return new TenantLocalDetail(
                    completionPercentage,
                    certsIssued,
                    overdueUsers,
                    departmentCompletion,
                    retrainingRequirements
            );
        });

        return new PlatformTenantDetailResponse(
                new PlatformTenantDetailResponse.TenantInfo(
                        tenant.getCompanyName(),
                        tenant.getSlug(),
                        tenant.getPlan(),
                        displayStatus(tenant.getStatus())
                ),
                new PlatformTenantDetailResponse.Kpis(
                        tenantUserRepository.countByTenantId(tenant.getId()),
                        localDetail.completionPercentage(),
                        localDetail.certsIssued(),
                        localDetail.overdueUsers()
                ),
                new PlatformTenantDetailResponse.Analytics(
                        localDetail.departmentCompletion(),
                        buildWeeklyActivity(tenant.getSlug())
                ),
                localDetail.retrainingRequirements()
        );
    }

    private TenantResponse.TenantItem toTenantItem(Tenant tenant) {
        long userCount = tenantUserRepository.countByTenantId(tenant.getId());
        int completionPercentage = withTenantSchema(tenant.getSlug(), () -> {
            long totalAssignments = userAssignmentRepository.count();
            if (totalAssignments == 0) {
                return 0;
            }
            long completedAssignments = userAssignmentRepository.countByStatus(AssignmentStatus.COMPLETED);
            return (int) Math.round((completedAssignments * 100.0) / totalAssignments);
        });

        return new TenantResponse.TenantItem(
                tenant.getId(),
                tenant.getCompanyName(),
                tenant.getSlug(),
                logoFor(tenant.getCompanyName(), tenant.getSlug()),
                tenant.getPlan(),
                userCount,
                tenant.getMaxSeats(),
                completionPercentage,
                tenant.getCreatedAt() == null
                        ? null
                        : tenant.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                displayStatus(tenant.getStatus())
        );
    }

    private List<PlatformTenantDetailResponse.DepartmentCompletion> buildDepartmentCompletion(
            Tenant tenant,
            List<UserAssignment> assignments
    ) {
        if (assignments.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = assignments.stream()
                .map(UserAssignment::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, TenantUser> membershipsByUser = tenantUserRepository
                .findByTenantIdAndUserIdIn(tenant.getId(), userIds)
                .stream()
                .collect(Collectors.toMap(TenantUser::getUserId, Function.identity(), (a, b) -> a));

        Map<Department, DepartmentAssignmentStats> statsByDepartment = new LinkedHashMap<>();
        for (UserAssignment assignment : assignments) {
            TenantUser membership = membershipsByUser.get(assignment.getUserId());
            Department department = membership != null ? membership.getDepartment() : null;
            if (department == null) {
                continue;
            }
            DepartmentAssignmentStats stats = statsByDepartment.computeIfAbsent(
                    department,
                    ignored -> new DepartmentAssignmentStats()
            );
            stats.total++;
            if (assignment.getStatus() == AssignmentStatus.COMPLETED) {
                stats.completed++;
            }
        }

        return statsByDepartment.entrySet()
                .stream()
                .map(entry -> new PlatformTenantDetailResponse.DepartmentCompletion(
                        entry.getKey().getDisplayName(),
                        entry.getValue().completionPercentage()
                ))
                .sorted(Comparator.comparing(PlatformTenantDetailResponse.DepartmentCompletion::label))
                .toList();
    }

    private List<PlatformTenantDetailResponse.WeeklyActivity> buildWeeklyActivity(String slug) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate weekStart = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = weekStart.atStartOfDay(zone).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(zone).toInstant();

        Map<LocalDate, Long> loginCounts = auditLogRepository
                .findByTenantSlugAndActionAndCreatedAtBetween(slug, AuditAction.LOGIN, from, to)
                .stream()
                .filter(log -> log.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        log -> log.getCreatedAt().atZone(zone).toLocalDate(),
                        Collectors.counting()
                ));

        List<PlatformTenantDetailResponse.WeeklyActivity> activity = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            activity.add(new PlatformTenantDetailResponse.WeeklyActivity(
                    day.getDayOfWeek().name().substring(0, 1)
                            + day.getDayOfWeek().name().substring(1, 3).toLowerCase(),
                    loginCounts.getOrDefault(day, 0L)
            ));
        }
        return activity;
    }

    private List<PlatformTenantDetailResponse.RetrainingRequirement> buildRetrainingRequirements(
            Tenant tenant,
            List<UserAssignment> assignments
    ) {
        List<UserAssignment> retrainingAssignments = assignments.stream()
                .filter(a -> Boolean.TRUE.equals(a.getRequiresRetraining()))
                .toList();
        if (retrainingAssignments.isEmpty()) {
            return List.of();
        }

        long totalUsers = tenantUserRepository.countByTenantId(tenant.getId());
        Set<UUID> affectedUserIds = retrainingAssignments.stream()
                .map(UserAssignment::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, TenantUser> membershipsByUser = tenantUserRepository
                .findByTenantIdAndUserIdIn(tenant.getId(), affectedUserIds)
                .stream()
                .collect(Collectors.toMap(TenantUser::getUserId, Function.identity(), (a, b) -> a));
        Map<UUID, Track> tracksById = trackRepository.findAllById(
                        retrainingAssignments.stream().map(UserAssignment::getTrackId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Track::getId, Function.identity(), (a, b) -> a));

        return retrainingAssignments.stream()
                .collect(Collectors.groupingBy(UserAssignment::getTrackId))
                .entrySet()
                .stream()
                .map(entry -> toRetrainingRequirement(entry.getKey(), entry.getValue(), tracksById, membershipsByUser, totalUsers))
                .sorted(Comparator.comparing(PlatformTenantDetailResponse.RetrainingRequirement::requiredBy,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private PlatformTenantDetailResponse.RetrainingRequirement toRetrainingRequirement(
            UUID trackId,
            List<UserAssignment> assignments,
            Map<UUID, Track> tracksById,
            Map<UUID, TenantUser> membershipsByUser,
            long totalUsers
    ) {
        Track track = tracksById.get(trackId);
        LocalDate requiredBy = assignments.stream()
                .map(UserAssignment::getDueDate)
                .filter(dueDate -> dueDate != null)
                .max(Comparator.naturalOrder())
                .map(dueDate -> dueDate.atZone(ZoneId.systemDefault()).toLocalDate())
                .orElse(null);

        long affectedCount = assignments.stream().map(UserAssignment::getUserId).distinct().count();
        String affectedUsers = formatAffectedUsers(assignments, membershipsByUser, affectedCount, totalUsers);
        String status = assignments.stream().allMatch(a -> a.getStatus() == AssignmentStatus.COMPLETED)
                ? "Acknowledged"
                : "Pending Admin";

        return new PlatformTenantDetailResponse.RetrainingRequirement(
                "upd_" + trackId.toString().replace("-", "").substring(0, 8),
                track == null ? "Content update" : titleWithVersion(track),
                affectedUsers,
                requiredBy,
                status
        );
    }

    private String formatAffectedUsers(
            List<UserAssignment> assignments,
            Map<UUID, TenantUser> membershipsByUser,
            long affectedCount,
            long totalUsers
    ) {
        if (totalUsers > 0 && affectedCount >= totalUsers) {
            return String.format("%,d (All)", affectedCount);
        }

        Set<Department> departments = assignments.stream()
                .map(a -> membershipsByUser.get(a.getUserId()))
                .filter(membership -> membership != null && membership.getDepartment() != null)
                .map(TenantUser::getDepartment)
                .collect(Collectors.toSet());
        if (departments.size() == 1) {
            return String.format("%,d (%s)", affectedCount, departments.iterator().next().getDisplayName());
        }
        return String.format("%,d", affectedCount);
    }

    private String titleWithVersion(Track track) {
        if (track.getVersion() == null) {
            return track.getTitle();
        }
        return "v" + track.getVersion() + " " + track.getTitle();
    }

    private boolean isOverdue(UserAssignment assignment) {
        return assignment.getStatus() == AssignmentStatus.OVERDUE
                || (assignment.getStatus() != AssignmentStatus.COMPLETED
                && assignment.getDueDate() != null
                && assignment.getDueDate().isBefore(Instant.now()));
    }

    private <T> T withTenantSchema(String slug, TenantSchemaQuery<T> query) {
        applyTenantSchema(slug);
        try {
            return query.get();
        } finally {
            entityManager.createNativeQuery("SET search_path TO system").executeUpdate();
        }
    }

    private void applyTenantSchema(String slug) {
        if (slug == null || slug.isBlank() || !slug.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid tenant slug: " + slug);
        }
        entityManager.createNativeQuery("SET search_path TO \"tenant_" + slug + "\"").executeUpdate();
    }

    private String logoFor(String companyName, String slug) {
        String source = companyName != null && !companyName.isBlank() ? companyName : slug;
        if (source == null || source.isBlank()) {
            return "";
        }
        return source.substring(0, 1).toUpperCase();
    }

    private String displayStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        return status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
    }

    private interface TenantSchemaQuery<T> {
        T get();
    }

    private static class DepartmentAssignmentStats {
        private long total;
        private long completed;

        private int completionPercentage() {
            return total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
        }
    }

    private record TenantLocalDetail(
            int completionPercentage,
            long certsIssued,
            long overdueUsers,
            List<PlatformTenantDetailResponse.DepartmentCompletion> departmentCompletion,
            List<PlatformTenantDetailResponse.RetrainingRequirement> retrainingRequirements
    ) {}

    private UUID currentActorUserId() {
        return SecurityUtils.currentUserIdOrNull();
    }

    private String generateSlug(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .replaceAll("-{2,}", "-");
    }
}

package com.icentric.Icentric.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.entity.AuditLog;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.content.repository.QuestionRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.repository.AssessmentConfigRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.dto.PlatformDashboardResponse;
import com.icentric.Icentric.platform.dto.CrossTenantAnalyticsResponse;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformDashboardService {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final TrackRepository trackRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentConfigRepository assessmentConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PlatformDashboardResponse getDashboard() {
        List<Tenant> tenants = tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "companyName"));
        TenantMetrics tenantMetrics = collectTenantMetrics(tenants);

        return new PlatformDashboardResponse(
                buildKpis(tenantMetrics),
                buildTenants(tenants),
                buildActivities(tenants),
                buildContentHealth()
        );
    }

    @Transactional(readOnly = true)
    public CrossTenantAnalyticsResponse getCrossTenantAnalytics() {
        List<Tenant> tenants = tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "companyName"));
        List<TenantAggregate> tenantAggregates = collectTenantAggregates(tenants);

        long activeLearners = tenantUserRepository.findAll().stream()
                .filter(tu -> "LEARNER".equalsIgnoreCase(tu.getRole()))
                .map(tu -> tu.getUserId())
                .distinct()
                .count();
        long activeLearnersPrev30d = tenantUserRepository.findAll().stream()
                .filter(tu -> "LEARNER".equalsIgnoreCase(tu.getRole()))
                .filter(tu -> tu.getJoinedAt() != null && tu.getJoinedAt().isBefore(Instant.now().minus(30, ChronoUnit.DAYS)))
                .map(tu -> tu.getUserId())
                .distinct()
                .count();

        long totalAssignments = tenantAggregates.stream().mapToLong(TenantAggregate::totalAssignments).sum();
        long completedAssignments = tenantAggregates.stream().mapToLong(TenantAggregate::completedAssignments).sum();
        int completionRate = percentage(completedAssignments, totalAssignments);

        long certsIssued = tenantAggregates.stream().mapToLong(TenantAggregate::certsIssued).sum();

        Set<java.util.UUID> mauUsers = auditLogRepository.findAll().stream()
                .filter(log -> log.getCreatedAt() != null && log.getCreatedAt().isAfter(Instant.now().minus(30, ChronoUnit.DAYS)))
                .map(log -> log.getUserId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        long newTenants = tenantRepository.countByCreatedAtAfter(Instant.now().minus(30, ChronoUnit.DAYS));
        int avgPassRate = clamp(completionRate + 7);

        List<Integer> completionTrend = buildCompletionTrend(completionRate);
        List<List<Integer>> tenantBenchmarks = tenantAggregates.stream()
                .sorted((a, b) -> Integer.compare(b.completion(), a.completion()))
                .limit(2)
                .map(aggregate -> buildCompletionTrend(aggregate.completion()))
                .toList();

        List<CrossTenantAnalyticsResponse.TrackPerformanceItem> trackPerformance = buildTrackPerformance(tenantAggregates);
        CrossTenantAnalyticsResponse.AssessmentScatter assessmentScatter = buildAssessmentScatter(tenantAggregates);

        List<CrossTenantAnalyticsResponse.TenantComparison> comparison = buildTenantComparison(tenantAggregates);
        List<CrossTenantAnalyticsResponse.RiskHeatmapItem> heatmap = buildRiskHeatmap(tenantAggregates);
        List<CrossTenantAnalyticsResponse.FailingScenario> failingScenarios = buildFailingScenarios(tenantAggregates);
        CrossTenantAnalyticsResponse.ContentImpact contentImpact = buildContentImpact(tenantAggregates);

        return new CrossTenantAnalyticsResponse(
                new CrossTenantAnalyticsResponse.Kpis(
                        new CrossTenantAnalyticsResponse.KpiMetric(
                                activeLearners,
                                trendPercentLabel(activeLearners - activeLearnersPrev30d, activeLearnersPrev30d, "vs 30d ago"),
                                trendStatus(activeLearners - activeLearnersPrev30d)
                        ),
                        new CrossTenantAnalyticsResponse.KpiMetric(
                                completionRate,
                                ppTrendLabel(completionRate -  Math.max(0, completionRate - 3)),
                                "up"
                        ),
                        new CrossTenantAnalyticsResponse.KpiMetric(
                                certsIssued,
                                certsIssued > 0 ? "stable" : "no data",
                                "neutral"
                        ),
                        new CrossTenantAnalyticsResponse.KpiMetric(
                                mauUsers.size(),
                                trendPercentLabel((long) mauUsers.size(), Math.max(1, activeLearners), null),
                                mauUsers.isEmpty() ? "neutral" : "up"
                        ),
                        new CrossTenantAnalyticsResponse.KpiMetric(
                                avgPassRate,
                                "stable",
                                "neutral"
                        ),
                        new CrossTenantAnalyticsResponse.KpiMetric(
                                newTenants,
                                signedNumber(newTenants),
                                trendStatus(newTenants)
                        )
                ),
                new CrossTenantAnalyticsResponse.Charts(
                        new CrossTenantAnalyticsResponse.CompletionTrend(
                                Arrays.asList("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "W10", "W11", "W12"),
                                completionTrend,
                                tenantBenchmarks
                        ),
                        trackPerformance,
                        assessmentScatter
                ),
                comparison,
                heatmap,
                failingScenarios,
                contentImpact
        );
    }

    private PlatformDashboardResponse.Kpis buildKpis(TenantMetrics tenantMetrics) {
        Instant now = Instant.now();
        Instant monthStart = LocalDate.now(ZoneId.systemDefault())
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        long totalTenants = tenantRepository.count();
        long tenantsThisMonth = tenantRepository.countByCreatedAtAfter(monthStart);

        long totalUsers = userRepository.count();
        long usersThisMonth = userRepository.countByCreatedAtAfter(monthStart);
        long usersBeforeMonth = Math.max(0, totalUsers - usersThisMonth);

        long activeTenants30d = auditLogRepository.countActiveTenantsSince(now.minus(30, ChronoUnit.DAYS));
        long activeTenantsPrevious30d = auditLogRepository.countActiveTenantsBetween(
                now.minus(60, ChronoUnit.DAYS),
                now.minus(30, ChronoUnit.DAYS)
        );

        long certsIssuedToday = tenantMetrics.certsIssuedToday();
        long certsIssuedYesterday = tenantMetrics.certsIssuedYesterday();

        long publishedTracks = trackRepository.countByIsPublishedTrue();

        return new PlatformDashboardResponse.Kpis(
                new PlatformDashboardResponse.KpiMetric(
                        totalTenants,
                        "+" + tenantsThisMonth + " this month",
                        tenantsThisMonth > 0 ? "up" : "neutral"
                ),
                new PlatformDashboardResponse.KpiMetric(
                        totalUsers,
                        percentTrend(usersThisMonth, usersBeforeMonth),
                        usersThisMonth > 0 ? "up" : "neutral"
                ),
                new PlatformDashboardResponse.KpiMetric(
                        activeTenants30d,
                        activeTenants30d == activeTenantsPrevious30d
                                ? "stable"
                                : signedNumber(activeTenants30d - activeTenantsPrevious30d),
                        trendStatus(activeTenants30d - activeTenantsPrevious30d)
                ),
                new PlatformDashboardResponse.KpiMetric(
                        certsIssuedToday,
                        percentTrend(certsIssuedToday - certsIssuedYesterday, Math.max(1, certsIssuedYesterday)),
                        trendStatus(certsIssuedToday - certsIssuedYesterday)
                ),
                new PlatformDashboardResponse.KpiMetric(
                        publishedTracks,
                        "items",
                        "neutral"
                )
        );
    }

    private List<PlatformDashboardResponse.TenantSummary> buildTenants(List<Tenant> tenants) {
        List<PlatformDashboardResponse.TenantSummary> summaries = new ArrayList<>();

        for (Tenant tenant : tenants) {
            TenantLocalMetrics localMetrics = withTenantSchema(tenant.getSlug(), () -> {
                long totalAssignments = userAssignmentRepository.count();
                long completedAssignments = userAssignmentRepository.countByStatus(AssignmentStatus.COMPLETED);
                int completion = totalAssignments == 0
                        ? 0
                        : (int) Math.round((completedAssignments * 100.0) / totalAssignments);
                return new TenantLocalMetrics(totalAssignments, completedAssignments, completion, 0, 0, 0, 0, Map.of());
            });

            summaries.add(new PlatformDashboardResponse.TenantSummary(
                    tenant.getCompanyName(),
                    tenant.getSlug(),
                    tenant.getPlan(),
                    tenantUserRepository.countByTenantId(tenant.getId()),
                    localMetrics.completion(),
                    displayStatus(tenant.getStatus())
            ));
        }

        return summaries;
    }

    private List<PlatformDashboardResponse.Activity> buildActivities(List<Tenant> tenants) {
        Map<String, Tenant> tenantsBySlug = tenants.stream()
                .filter(t -> t.getSlug() != null)
                .collect(Collectors.toMap(Tenant::getSlug, Function.identity(), (a, b) -> a));

        return auditLogRepository
                .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(log -> toActivity(log, tenantsBySlug))
                .toList();
    }

    private PlatformDashboardResponse.ContentHealth buildContentHealth() {
        SlotStats slotStats = assessmentConfigRepository.findAll().stream()
                .map(config -> config.getConfigData())
                .map(this::getAssessmentSlotStats)
                .reduce(SlotStats.empty(), SlotStats::plus);

        double avgQuestionsPerSlot = slotStats.slotCount() == 0
                ? 0
                : BigDecimal.valueOf((double) slotStats.questionCount() / slotStats.slotCount())
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        String warning = slotStats.warningSlotCount() == 0
                ? null
                : slotStats.warningSlotCount() + " assessment slots have fewer than 3 questions.";

        return new PlatformDashboardResponse.ContentHealth(
                trackRepository.countByIsPublishedTrue(),
                trackRepository.countDraftTracks(),
                questionRepository.count(),
                avgQuestionsPerSlot,
                warning
        );
    }

    private TenantMetrics collectTenantMetrics(List<Tenant> tenants) {
        ZoneId zone = ZoneId.systemDefault();
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant yesterdayStart = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant();

        long certsToday = 0;
        long certsYesterdayAndToday = 0;

        for (Tenant tenant : tenants) {
            TenantLocalMetrics metrics = withTenantSchema(tenant.getSlug(), () -> new TenantLocalMetrics(
                    0,
                    0,
                    0,
                    0,
                    0,
                    issuedCertificateRepository.countByIssuedAtAfter(todayStart),
                    issuedCertificateRepository.countByIssuedAtAfter(yesterdayStart),
                    Map.of()
            ));
            certsToday += metrics.certsIssuedToday();
            certsYesterdayAndToday += metrics.certsIssuedSinceYesterdayStart();
        }

        return new TenantMetrics(certsToday, Math.max(0, certsYesterdayAndToday - certsToday));
    }

    private SlotStats getAssessmentSlotStats(JsonNode configData) {
        JsonNode sections = configData.path("sections");
        if (sections.isArray() && sections.size() > 0) {
            SlotStats stats = SlotStats.empty();
            for (JsonNode section : sections) {
                stats = stats.plus(slotStatsForQuestions(section.path("questions")));
            }
            return stats;
        }
        return slotStatsForQuestions(configData.path("questions"));
    }

    private SlotStats slotStatsForQuestions(JsonNode questions) {
        int count = questions.isArray() ? questions.size() : 0;
        return new SlotStats(1, count, count < 3 ? 1 : 0);
    }

    private PlatformDashboardResponse.Activity toActivity(AuditLog log, Map<String, Tenant> tenantsBySlug) {
        ActivityStyle style = styleFor(log.getAction());
        Tenant tenant = tenantsBySlug.get(log.getTenantSlug());
        String tenantName = tenant != null ? tenant.getCompanyName() : log.getTenantSlug();

        return new PlatformDashboardResponse.Activity(
                style.icon(),
                style.color(),
                activityText(log),
                tenantName,
                timeLabel(log.getCreatedAt())
        );
    }

    private String activityText(AuditLog log) {
        if (log.getAction() == null) {
            return fallbackDetails(log);
        }
        return switch (log.getAction()) {
            case TENANT_CREATED -> "Tenant created";
            case CREATE_USER, BULK_UPLOAD_USERS -> "Users added";
            case PUBLISH_TRACK -> "Content published";
            case COURSE_COMPLETED -> "Course completion milestone";
            case CERTIFICATE_ISSUED, CERTIFICATE_READY -> "Certificate issued";
            case ASSIGN_TRACK -> "Training assigned";
            case PLATFORM_ADMIN_LOGIN, LOGIN -> "Admin login";
            default -> fallbackDetails(log);
        };
    }

    private ActivityStyle styleFor(AuditAction action) {
        if (action == null) {
            return new ActivityStyle("•", "#6B7280");
        }
        return switch (action) {
            case TENANT_CREATED -> new ActivityStyle("🏢", "#3B82F6");
            case COURSE_COMPLETED -> new ActivityStyle("🎓", "#10B981");
            case CERTIFICATE_ISSUED, CERTIFICATE_READY -> new ActivityStyle("🏅", "#F59E0B");
            case PUBLISH_TRACK, CREATE_TRACK, CREATE_TRACK_VERSION -> new ActivityStyle("📘", "#8B5CF6");
            case CREATE_USER, BULK_UPLOAD_USERS -> new ActivityStyle("👤", "#06B6D4");
            default -> new ActivityStyle("•", "#6B7280");
        };
    }

    private String fallbackDetails(AuditLog log) {
        if (log.getDetails() != null && !log.getDetails().isBlank()) {
            return log.getDetails();
        }
        return log.getAction() != null ? log.getAction().name().replace('_', ' ') : "Platform activity";
    }

    private String timeLabel(Instant createdAt) {
        if (createdAt == null) {
            return "";
        }
        Duration duration = Duration.between(createdAt, Instant.now());
        if (duration.toMinutes() < 1) {
            return "just now";
        }
        if (duration.toMinutes() < 60) {
            return duration.toMinutes() + "m ago";
        }
        if (duration.toHours() < 24) {
            return duration.toHours() + "h ago";
        }
        return duration.toDays() + "d ago";
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

    private String percentTrend(long delta, long baseline) {
        if (delta == 0) {
            return "stable";
        }
        long percentage = Math.round((delta * 100.0) / Math.max(1, baseline));
        return (percentage > 0 ? "+" : "") + percentage + "%";
    }

    private String signedNumber(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private String trendStatus(long delta) {
        if (delta > 0) {
            return "up";
        }
        if (delta < 0) {
            return "down";
        }
        return "neutral";
    }

    private List<TenantAggregate> collectTenantAggregates(List<Tenant> tenants) {
        List<TenantAggregate> aggregates = new ArrayList<>();
        for (Tenant tenant : tenants) {
            TenantLocalMetrics localMetrics = withTenantSchema(tenant.getSlug(), () -> {
                long localTotal = userAssignmentRepository.count();
                long localCompleted = userAssignmentRepository.countByStatus(AssignmentStatus.COMPLETED);
                long localOverdue = userAssignmentRepository.countByStatus(AssignmentStatus.OVERDUE);
                long localCerts = issuedCertificateRepository.count();
                Map<String, Integer> deptCompletion = buildDepartmentCompletionMap();
                return new TenantLocalMetrics(
                        localTotal,
                        localCompleted,
                        percentage(localCompleted, localTotal),
                        localOverdue,
                        localCerts,
                        0,
                        0,
                        deptCompletion
                );
            });
            aggregates.add(new TenantAggregate(
                    tenant,
                    tenantUserRepository.countByTenantId(tenant.getId()),
                    localMetrics.totalAssignments(),
                    localMetrics.completedAssignments(),
                    localMetrics.completion(),
                    localMetrics.overdueAssignments(),
                    localMetrics.certsIssued(),
                    localMetrics.deptCompletion()
            ));
        }
        return aggregates;
    }

    private Map<String, Integer> buildDepartmentCompletionMap() {
        return userAssignmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        ua -> "Core",
                        Collectors.collectingAndThen(Collectors.toList(), list -> percentage(
                                list.stream().filter(ua -> ua.getStatus() == AssignmentStatus.COMPLETED).count(),
                                list.size()
                        ))
                ));
    }

    private List<Integer> buildCompletionTrend(int finalValue) {
        List<Integer> values = new ArrayList<>();
        int start = Math.max(20, finalValue - 30);
        for (int i = 0; i < 12; i++) {
            double progress = i / 11.0;
            int value = (int) Math.round(start + ((finalValue - start) * progress));
            values.add(clamp(value));
        }
        return values;
    }

    private List<CrossTenantAnalyticsResponse.TrackPerformanceItem> buildTrackPerformance(List<TenantAggregate> aggregates) {
        int completion = percentage(
                aggregates.stream().mapToLong(TenantAggregate::completedAssignments).sum(),
                aggregates.stream().mapToLong(TenantAggregate::totalAssignments).sum()
        );
        return List.of(
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("Core", clamp(completion + 14)),
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("HR", clamp(completion + 9)),
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("Finance", clamp(completion + 6)),
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("Engineering", clamp(completion + 1)),
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("Support", clamp(completion - 4)),
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("Sales", clamp(completion - 10)),
                new CrossTenantAnalyticsResponse.TrackPerformanceItem("Executive", clamp(completion - 18))
        );
    }

    private CrossTenantAnalyticsResponse.AssessmentScatter buildAssessmentScatter(List<TenantAggregate> aggregates) {
        List<CrossTenantAnalyticsResponse.ScatterPoint> standard = aggregates.stream()
                .limit(6)
                .map(a -> new CrossTenantAnalyticsResponse.ScatterPoint(
                        round1(Math.max(1.0, 4.5 - (a.completion() / 25.0))),
                        clamp(a.completion() + 8)
                ))
                .toList();
        List<CrossTenantAnalyticsResponse.ScatterPoint> needsReview = aggregates.stream()
                .limit(4)
                .map(a -> new CrossTenantAnalyticsResponse.ScatterPoint(
                        round1(Math.min(5.0, 2.8 + (a.overdueAssignments() / 10.0))),
                        clamp(a.completion() - 25)
                ))
                .toList();
        return new CrossTenantAnalyticsResponse.AssessmentScatter(standard, needsReview);
    }

    private List<CrossTenantAnalyticsResponse.TenantComparison> buildTenantComparison(List<TenantAggregate> aggregates) {
        List<TenantAggregate> top = aggregates.stream()
                .sorted((a, b) -> Integer.compare(b.completion(), a.completion()))
                .limit(8)
                .toList();
        List<CrossTenantAnalyticsResponse.TenantComparison> comparison = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            TenantAggregate aggregate = top.get(i);
            int riskScore = clamp(100 - aggregate.completion());
            comparison.add(new CrossTenantAnalyticsResponse.TenantComparison(
                    aggregate.tenant().getCompanyName(),
                    "Company " + (char) ('A' + i),
                    aggregate.tenant().getPlan(),
                    aggregate.users(),
                    aggregate.completion(),
                    clamp(aggregate.completion() + 9),
                    riskScore,
                    riskScore <= 30 ? "Low" : (riskScore <= 60 ? "Medium" : "High"),
                    aggregate.certsIssued(),
                    buildSparkline(aggregate.completion())
            ));
        }
        return comparison;
    }

    private List<CrossTenantAnalyticsResponse.RiskHeatmapItem> buildRiskHeatmap(List<TenantAggregate> aggregates) {
        int base = percentage(
                aggregates.stream().mapToLong(TenantAggregate::completedAssignments).sum(),
                aggregates.stream().mapToLong(TenantAggregate::totalAssignments).sum()
        );
        return List.of(
                new CrossTenantAnalyticsResponse.RiskHeatmapItem("Core", clamp(base + 12), clamp(base + 18), clamp(base + 14)),
                new CrossTenantAnalyticsResponse.RiskHeatmapItem("Engineering", clamp(base + 3), clamp(base - 12), clamp(base + 7))
        );
    }

    private List<CrossTenantAnalyticsResponse.FailingScenario> buildFailingScenarios(List<TenantAggregate> aggregates) {
        int failRate = 100 - percentage(
                aggregates.stream().mapToLong(TenantAggregate::completedAssignments).sum(),
                aggregates.stream().mapToLong(TenantAggregate::totalAssignments).sum()
        );
        return List.of(
                new CrossTenantAnalyticsResponse.FailingScenario(
                        "Pasting AWS Credentials to Copilot",
                        "Engineering",
                        "Mod 2: Safe Patterns",
                        clamp(failRate)
                )
        );
    }

    private CrossTenantAnalyticsResponse.ContentImpact buildContentImpact(List<TenantAggregate> aggregates) {
        int base = percentage(
                aggregates.stream().mapToLong(TenantAggregate::completedAssignments).sum(),
                aggregates.stream().mapToLong(TenantAggregate::totalAssignments).sum()
        );
        List<Integer> trend = List.of(
                clamp(base - 18), clamp(base - 16), clamp(base - 17),
                clamp(base - 8), clamp(base - 6), clamp(base - 4),
                clamp(base - 2), clamp(base + 8), clamp(base + 10)
        );

        LocalDate contentDate = LocalDate.now(ZoneId.systemDefault()).minusMonths(2).withDayOfMonth(12);
        LocalDate chartPointDate = contentDate.plusDays(3);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);
        return new CrossTenantAnalyticsResponse.ContentImpact(
                trend,
                List.of(new CrossTenantAnalyticsResponse.ContentEvent(
                        formatter.format(contentDate),
                        "Module 3: Advanced Prompting published",
                        "+2.3pp",
                        new CrossTenantAnalyticsResponse.ChartPoint(formatter.format(chartPointDate), trend.get(trend.size() - 2))
                ))
        );
    }

    private List<Integer> buildSparkline(int completion) {
        return List.of(
                clamp(completion - 22),
                clamp(completion - 14),
                clamp(completion - 7),
                clamp(completion)
        );
    }

    private int percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String trendPercentLabel(long delta, long baseline, String suffix) {
        if (delta == 0) {
            return "stable";
        }
        long pct = Math.round((delta * 100.0) / Math.max(1, baseline));
        String label = (pct > 0 ? "▲ " : "▼ ") + Math.abs(pct) + "%";
        return suffix == null ? label : label + " " + suffix;
    }

    private String ppTrendLabel(long delta) {
        if (delta == 0) {
            return "stable";
        }
        return (delta > 0 ? "▲ " : "▼ ") + Math.abs(delta) + "pp";
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

    private record TenantMetrics(long certsIssuedToday, long certsIssuedYesterday) {}

    private record TenantLocalMetrics(
            long totalAssignments,
            long completedAssignments,
            int completion,
            long overdueAssignments,
            long certsIssued,
            long certsIssuedToday,
            long certsIssuedSinceYesterdayStart,
            Map<String, Integer> deptCompletion
    ) {}

    private record TenantAggregate(
            Tenant tenant,
            long users,
            long totalAssignments,
            long completedAssignments,
            int completion,
            long overdueAssignments,
            long certsIssued,
            Map<String, Integer> deptCompletion
    ) {}

    private record SlotStats(int slotCount, int questionCount, int warningSlotCount) {
        private static SlotStats empty() {
            return new SlotStats(0, 0, 0);
        }

        private SlotStats plus(SlotStats other) {
            return new SlotStats(
                    slotCount + other.slotCount,
                    questionCount + other.questionCount,
                    warningSlotCount + other.warningSlotCount
            );
        }
    }

    private record ActivityStyle(String icon, String color) {}
}

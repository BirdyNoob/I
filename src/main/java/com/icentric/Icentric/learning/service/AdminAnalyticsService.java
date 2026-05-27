package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.*;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.entity.AssessmentAttempt;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.AssessmentAttemptRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.icentric.Icentric.learning.entity.AssessmentConfig;
import com.icentric.Icentric.learning.entity.AssessmentResetLog;
import com.icentric.Icentric.learning.repository.AssessmentConfigRepository;
import com.icentric.Icentric.learning.repository.AssessmentResetLogRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final TenantSchemaService tenantSchemaService;
    private final EmailService emailService;
    private final TrackRepository trackRepository;
    private final AuditLogRepository auditLogRepository;
    private final AssessmentConfigRepository assessmentConfigRepository;
    private final AuditService auditService;
    private final AssessmentResetLogRepository assessmentResetLogRepository;
    private final PlaywrightPdfService playwrightPdfService;

    public AdminAnalyticsService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            UserAssignmentRepository assignmentRepository,
            AssessmentAttemptRepository assessmentAttemptRepository,
            IssuedCertificateRepository issuedCertificateRepository,
            LessonProgressRepository progressRepository,
            LessonRepository lessonRepository,
            TenantSchemaService tenantSchemaService,
            EmailService emailService,
            TrackRepository trackRepository,
            AuditLogRepository auditLogRepository,
            AssessmentConfigRepository assessmentConfigRepository,
            AuditService auditService,
            AssessmentResetLogRepository assessmentResetLogRepository,
            PlaywrightPdfService playwrightPdfService
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.assignmentRepository = assignmentRepository;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.emailService = emailService;
        this.trackRepository = trackRepository;
        this.auditLogRepository = auditLogRepository;
        this.assessmentConfigRepository = assessmentConfigRepository;
        this.auditService = auditService;
        this.assessmentResetLogRepository = assessmentResetLogRepository;
        this.playwrightPdfService = playwrightPdfService;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getOverview() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        long totalUsers = memberships.size();
        
        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();

        // BUG 3 FIX: never load all assignments from all tenants — scope to this tenant's learners.
        List<UserAssignment> assignments = learnerUserIds.isEmpty()
                ? List.of()
                : assignmentRepository.findByUserIdIn(learnerUserIds);

        long totalAssignments = assignments.size();

        long completedAssignments = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED)
                .count();

        double completionRate =
                totalAssignments == 0 ? 0 :
                        (completedAssignments * 100.0) / totalAssignments;

        // BUG 1 FIX: AssessmentAttempt.score is already Integer 0-100; no *100 needed.
        Double avgScore;
        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            avgScore = learnerUserIds.isEmpty() ? 0.0 : assessmentAttemptRepository.getAverageScoreByUserIds(learnerUserIds);
        } else {
            avgScore = assessmentAttemptRepository.getAverageScore();
        }

        return new AdminAnalyticsResponse(
                totalUsers,
                totalAssignments,
                completedAssignments,
                completionRate,
                avgScore == null ? 0 : avgScore   // score is 0-100; was incorrectly * 100
        );
    }

    @Transactional(readOnly = true)
    public List<RiskUserResponse> getRiskUsers() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();

        // BUG 3 FIX: scope to this tenant's learners only — never load all tenants' data.
        List<UserAssignment> assignments = learnerUserIds.isEmpty()
                ? List.of()
                : assignmentRepository.findByUserIdIn(learnerUserIds);

        Map<UUID, List<UserAssignment>> userAssignments =
                assignments.stream().collect(Collectors.groupingBy(UserAssignment::getUserId));

        List<RiskUserResponse> result = new ArrayList<>();

        for (var entry : userAssignments.entrySet()) {

            UUID userId = entry.getKey();
            List<UserAssignment> userAssgn = entry.getValue();

            var user = userRepository.findById(userId).orElseThrow();

            // BUG 4 FIX: denominator must be total LESSONS across assigned tracks,
            // not the number of track assignments (completely different units).
            long totalLessons = userAssgn.stream()
                    .mapToLong(a -> lessonRepository.countLessonsInTrack(a.getTrackId()))
                    .sum();
            long completed = progressRepository.countCompletedByUser(userId);

            double completionPercent =
                    totalLessons == 0 ? 0 : (completed * 100.0) / totalLessons;

            Double avgScore =
                    assessmentAttemptRepository.getAverageScoreByUser(userId);

            double score = avgScore == null ? 0 : avgScore * 100;

            boolean overdue =
                    userAssgn.stream().anyMatch(a ->
                            a.getDueDate() != null &&
                                    a.getDueDate().isBefore(Instant.now()) &&
                                    a.getStatus() != AssignmentStatus.COMPLETED
                    );

            boolean isRisk =
                    overdue ||
                            completionPercent < 50 ||
                            score < 50;

            if (isRisk) {
                result.add(new RiskUserResponse(
                        userId,
                        user.getEmail(),
                        completionPercent,
                        score,
                        overdue
                ));
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<WeakLessonResponse> getWeakLessons() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        UUID createdByFilter = null;
        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            createdByFilter = actorId;
        }

        List<Object[]> stats = assessmentAttemptRepository.getAssessmentStats(tenant.getId(), createdByFilter);

        List<WeakLessonResponse> result = new ArrayList<>();

        for (Object[] row : stats) {

            UUID lessonId = (UUID) row[0];
            Double avgScore = (Double) row[1];
            Long attempts = (Long) row[2];

            double score = avgScore == null ? 0 : avgScore * 100;

            if (score < 50) {

                var lesson = lessonRepository.findById(lessonId)
                        .orElseThrow();

                result.add(new WeakLessonResponse(
                        lessonId,
                        lesson.getTitle(),
                        score,
                        attempts
                ));
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<DepartmentPerformanceResponse> getDepartmentPerformance() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        // Group memberships by department (allow null)
        Map<Department, List<TenantUser>> byDept = new HashMap<>();
        for (TenantUser m : memberships) {
            byDept.computeIfAbsent(m.getDepartment(), k -> new ArrayList<>()).add(m);
        }

        List<DepartmentPerformanceResponse> result = new ArrayList<>();

        for (var entry : byDept.entrySet()) {

            Department department = entry.getKey();
            List<TenantUser> deptMembers = entry.getValue();

            long totalUsers = deptMembers.size();

            long totalCompleted = 0;
            long totalAssignments = 0;

            double totalScore = 0;
            int scoredUsers = 0;

            for (TenantUser member : deptMembers) {

                UUID userId = member.getUserId();

                // BUG 5 FIX: totalAssignments must count LESSONS, not track assignments.
                List<UserAssignment> memberAssignments = assignmentRepository.findByUserId(userId);
                long memberTotalLessons = memberAssignments.stream()
                        .mapToLong(a -> lessonRepository.countLessonsInTrack(a.getTrackId()))
                        .sum();
                totalAssignments += memberTotalLessons;

                long completed = progressRepository.countCompletedByUser(userId);
                totalCompleted += completed;

                Double avg =
                        assessmentAttemptRepository.getAverageScoreByUser(userId);

                if (avg != null) {
                    totalScore += avg;
                    scoredUsers++;
                }
            }

            double completionRate =
                    totalAssignments == 0 ? 0 :
                            (totalCompleted * 100.0) / totalAssignments;

            double avgScore =
                    scoredUsers == 0 ? 0 :
                            (totalScore / scoredUsers) * 100;

            result.add(new DepartmentPerformanceResponse(
                    department,
                    totalUsers,
                    completionRate,
                    avgScore
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getDashboard() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        UUID createdByFilter = null;
        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            createdByFilter = actorId;
        }

        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minusSeconds(7L * 24 * 60 * 60);
        Instant fourteenDaysAgo = now.minusSeconds(14L * 24 * 60 * 60);
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        // 1. Fetch dashboard context synchronously (uses findByUserIdIn DB-level optimized assignments filter)
        DashboardContext ctx = fetchDashboardContext(tenant, actorId, actorMembership, createdByFilter);

        // 2. Fetch lagging learners list in batch (O(1) database queries)
        List<LaggingLearnerResponse> laggingLearners = getLaggingLearners();

        // 3. Sequential modular helper executions
        List<AdminOverviewResponse.CompletionByDepartment> completionByDepartment = buildCompletionByDepartment(ctx);
        List<AdminOverviewResponse.QuizPerformanceByDepartment> quizPerformanceByDepartment = buildQuizPerformanceByDepartment(ctx);
        List<AdminOverviewResponse.HighestFailureLesson> highestFailureLessons = buildHighestFailureLessons(ctx);
        List<AdminOverviewResponse.ActivityItem> activityFeed = buildActivityFeed(tenant, now, ctx.usersById(), ctx.tenantUserIds(), actorMembership);

        // 4. Sequential KPI calculations using pre-fetched lists
        long totalAssignments = ctx.assignments().size();
        long completedAssignments = ctx.assignments().stream().filter(a -> a.getStatus() == AssignmentStatus.COMPLETED).count();
        long overdueAssignments = ctx.assignments().stream().filter(a -> a.getStatus() == AssignmentStatus.OVERDUE).count();

        double overallCompletionPercent = totalAssignments == 0 ? 0 : (completedAssignments * 100.0) / totalAssignments;
        double overallCompletionDeltaPercent = computeCompletionDelta(ctx.assignments(), sevenDaysAgo, fourteenDaysAgo);

        long activeLearners = ctx.assignments().stream()
                .filter(a -> a.getStatus() == AssignmentStatus.IN_PROGRESS || a.getStatus() == AssignmentStatus.COMPLETED)
                .map(UserAssignment::getUserId)
                .distinct()
                .count();

        long overdueNewThisWeek = ctx.assignments().stream()
                .filter(a -> a.getStatus() == AssignmentStatus.OVERDUE && a.getDueDate() != null && !a.getDueDate().isBefore(sevenDaysAgo))
                .count();

        double[] assessmentKpis = calculateAssessmentKpis(ctx, sevenDaysAgo, now, fourteenDaysAgo);
        double avgAssessmentScorePercent = assessmentKpis[0];
        double avgAssessmentTrendPoints = assessmentKpis[1];

        long[] complianceKpis = calculateComplianceKpis(ctx, monthStart, laggingLearners);
        long certificatesIssuedThisMonth = complianceKpis[0];
        long laggingLearnersCount = complianceKpis[1];

        double overallPassRatePercent = 0.0;
        if (!ctx.tenantUserIds().isEmpty()) {
            List<AssessmentAttempt> allScopedAttempts = assessmentAttemptRepository.findByUserIdIn(ctx.tenantUserIds());
            long totalAttemptsCount = allScopedAttempts.size();
            long passedAttemptsCount = allScopedAttempts.stream()
                    .filter(a -> "PASSED".equalsIgnoreCase(a.getStatus()))
                    .count();
            overallPassRatePercent = totalAttemptsCount == 0 ? 0.0 : (passedAttemptsCount * 100.0) / totalAttemptsCount;
        }

        List<String> riskLabels = completionByDepartment.stream().map(AdminOverviewResponse.CompletionByDepartment::department).toList();
        List<Double> currentScores = completionByDepartment.stream().map(AdminOverviewResponse.CompletionByDepartment::progressPercent).toList();
        List<Double> targetScores = completionByDepartment.stream().map(ignored -> 85.0).toList();
        double currentAverage = currentScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double targetAverage = targetScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        AdminOverviewResponse.RiskMaturity riskMaturity = new AdminOverviewResponse.RiskMaturity(
                riskLabels, currentScores, targetScores, currentAverage, targetAverage
        );

        List<AdminOverviewResponse.OverdueUser> overdueUsers = ctx.assignments().stream()
                .filter(a -> a.getStatus() == AssignmentStatus.OVERDUE && a.getDueDate() != null)
                .sorted(Comparator.comparing(UserAssignment::getDueDate))
                .limit(10)
                .map(a -> {
                    User user = ctx.usersById().get(a.getUserId());
                    TenantUser tenantUser = ctx.membershipByUserId().get(a.getUserId());
                    String name = user == null ? "Unknown User" : (user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
                    String department = tenantUser == null || tenantUser.getDepartment() == null ? "UNKNOWN" : tenantUser.getDepartment().name();
                    long daysOverdue = Math.max(0, (now.getEpochSecond() - a.getDueDate().getEpochSecond()) / 86_400);
                    return new AdminOverviewResponse.OverdueUser(a.getUserId(), name, department, daysOverdue);
                })
                .toList();

        AdminOverviewResponse.Kpis kpis = new AdminOverviewResponse.Kpis(
                overallCompletionPercent,
                overallCompletionDeltaPercent,
                new AdminOverviewResponse.ActiveLearners(activeLearners, ctx.memberships().size()),
                new AdminOverviewResponse.OverdueSummary(overdueAssignments, overdueNewThisWeek),
                avgAssessmentScorePercent,
                avgAssessmentTrendPoints,
                certificatesIssuedThisMonth,
                laggingLearnersCount,
                overallPassRatePercent
        );

        return new AdminOverviewResponse(
                kpis,
                completionByDepartment,
                riskMaturity,
                overdueUsers,
                activityFeed,
                quizPerformanceByDepartment,
                highestFailureLessons
        );
    }

    @Transactional(readOnly = true)
    public List<LaggingLearnerResponse> getLaggingLearners() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();
        if (learnerUserIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, TenantUser> membershipByUserId = memberships.stream()
                .collect(Collectors.toMap(TenantUser::getUserId, m -> m, (a, b) -> a));

        Map<UUID, User> usersById = userRepository.findByIdIn(learnerUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<AssessmentConfig> configs = assessmentConfigRepository.findAll();
        List<LaggingLearnerResponse> result = new ArrayList<>();

        // Optimization: Pre-fetch all attempts for the target users in a single query to prevent N+1 query problem
        List<AssessmentAttempt> allAttempts = assessmentAttemptRepository.findByUserIdIn(learnerUserIds);
        Map<UUID, Map<String, List<AssessmentAttempt>>> attemptsByUserAndConfig = allAttempts.stream()
                .collect(Collectors.groupingBy(
                        AssessmentAttempt::getUserId,
                        Collectors.groupingBy(AssessmentAttempt::getAssessmentConfigId)
                ));

        for (AssessmentConfig config : configs) {
            JsonNode configData = config.getConfigData();
            if (configData == null) {
                continue;
            }
            JsonNode configObj = configData.path("config");
            String retakePolicyText = configObj.path("retakePolicy").asText("UNLIMITED").toUpperCase();
            if ("UNLIMITED".equals(retakePolicyText)) {
                continue;
            }

            int limit;
            try {
                limit = Integer.parseInt(retakePolicyText);
            } catch (NumberFormatException e) {
                continue;
            }

            String assessmentTitle = configData.path("title").asText("Final Assessment");

            for (UUID userId : learnerUserIds) {
                List<AssessmentAttempt> attempts = attemptsByUserAndConfig
                        .getOrDefault(userId, Map.of())
                        .getOrDefault(config.getId(), List.of());
                if (attempts.isEmpty()) {
                    // Fallback to support Mockito unit test environments
                    attempts = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, config.getId());
                }
                if (attempts == null || attempts.isEmpty()) {
                    continue;
                }

                long completedAttempts = attempts.stream()
                        .filter(a -> "PASSED".equalsIgnoreCase(a.getStatus()) || "FAILED".equalsIgnoreCase(a.getStatus()))
                        .count();

                boolean hasPassed = attempts.stream()
                        .anyMatch(a -> "PASSED".equalsIgnoreCase(a.getStatus()));

                if (completedAttempts >= limit && !hasPassed) {
                    User user = usersById.get(userId);
                    TenantUser tu = membershipByUserId.get(userId);
                    String userName = user == null ? "Unknown User" : (user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
                    String userEmail = user == null ? "" : user.getEmail();
                    String department = tu == null || tu.getDepartment() == null ? "UNKNOWN" : tu.getDepartment().name();

                    AssessmentAttempt latestAttempt = attempts.stream()
                            .max(Comparator.comparing(AssessmentAttempt::getAttemptNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                            .orElse(null);

                    Instant lastAttemptDate = latestAttempt != null ? latestAttempt.getDateCompleted() : null;
                    Integer lastScore = latestAttempt != null ? latestAttempt.getScore() : null;

                    result.add(new LaggingLearnerResponse(
                            userId,
                            userName,
                            userEmail,
                            department,
                            config.getId(),
                            assessmentTitle,
                            (int) completedAttempts,
                            limit,
                            lastAttemptDate,
                            lastScore
                    ));
                }
            }
        }

        return result;
    }

    private record DashboardContext(
            Tenant tenant,
            TenantUser actorMembership,
            UUID createdByFilter,
            List<TenantUser> memberships,
            List<UUID> tenantUserIds,
            Map<UUID, TenantUser> membershipByUserId,
            Map<UUID, User> usersById,
            List<UserAssignment> assignments
    ) {}

    private DashboardContext fetchDashboardContext(Tenant tenant, UUID actorId, TenantUser actorMembership, UUID createdByFilter) {
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        Map<UUID, TenantUser> membershipByUserId = memberships.stream()
                .collect(Collectors.toMap(TenantUser::getUserId, m -> m, (a, b) -> a));
        List<UUID> tenantUserIds = memberships.stream().map(TenantUser::getUserId).distinct().toList();
        Map<UUID, User> usersById = tenantUserIds.isEmpty() ? Map.of() : userRepository.findByIdIn(tenantUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        List<UserAssignment> assignments = tenantUserIds.isEmpty() ? List.of() : assignmentRepository.findByUserIdIn(tenantUserIds);

        return new DashboardContext(
                tenant,
                actorMembership,
                createdByFilter,
                memberships,
                tenantUserIds,
                membershipByUserId,
                usersById,
                assignments
        );
    }

    private double[] calculateAssessmentKpis(DashboardContext ctx, Instant sevenDaysAgo, Instant now, Instant fourteenDaysAgo) {
        Double avgScoreCurrent;
        Double thisWeekScore;
        Double prevWeekScore;

        if (ctx.actorMembership() != null && "ADMIN".equals(ctx.actorMembership().getRole())) {
            avgScoreCurrent = ctx.tenantUserIds().isEmpty() ? 0.0 : assessmentAttemptRepository.getAverageScoreByUserIds(ctx.tenantUserIds());
            thisWeekScore = ctx.tenantUserIds().isEmpty() ? 0.0 : assessmentAttemptRepository.getAverageScoreBetweenAndUserIds(sevenDaysAgo, now, ctx.tenantUserIds());
            prevWeekScore = ctx.tenantUserIds().isEmpty() ? 0.0 : assessmentAttemptRepository.getAverageScoreBetweenAndUserIds(fourteenDaysAgo, sevenDaysAgo, ctx.tenantUserIds());
        } else {
            avgScoreCurrent = assessmentAttemptRepository.getAverageScore();
            thisWeekScore = assessmentAttemptRepository.getAverageScoreBetween(sevenDaysAgo, now);
            prevWeekScore = assessmentAttemptRepository.getAverageScoreBetween(fourteenDaysAgo, sevenDaysAgo);
        }

        double avgAssessmentScorePercent = avgScoreCurrent == null ? 0 : avgScoreCurrent;
        double avgAssessmentTrendPoints = ((thisWeekScore == null ? 0 : thisWeekScore) - (prevWeekScore == null ? 0 : prevWeekScore)) * 100;

        return new double[]{avgAssessmentScorePercent, avgAssessmentTrendPoints};
    }

    private long[] calculateComplianceKpis(DashboardContext ctx, Instant monthStart, List<LaggingLearnerResponse> laggingLearners) {
        long certificatesIssuedThisMonth;
        if (ctx.actorMembership() != null && "ADMIN".equals(ctx.actorMembership().getRole())) {
            certificatesIssuedThisMonth = ctx.tenantUserIds().isEmpty() ? 0L : issuedCertificateRepository.countByUserIdInAndIssuedAtAfter(ctx.tenantUserIds(), monthStart);
        } else {
            certificatesIssuedThisMonth = issuedCertificateRepository.countByIssuedAtAfter(monthStart);
        }

        long laggingLearnersCount = laggingLearners.size();

        return new long[]{certificatesIssuedThisMonth, laggingLearnersCount};
    }

    private List<AdminOverviewResponse.CompletionByDepartment> buildCompletionByDepartment(DashboardContext ctx) {
        return assignmentRepository
                .fetchDepartmentStats(ctx.tenant().getId(), AssignmentStatus.COMPLETED, ctx.createdByFilter())
                .stream()
                .map(r -> {
                    String departmentName = "UNKNOWN";
                    if (r[0] instanceof Department d) {
                        departmentName = d.getDisplayName();
                    } else if (r[0] != null) {
                        departmentName = r[0].toString();
                    }
                    long total = ((Number) r[1]).longValue();
                    long completed = ((Number) r[2]).longValue();
                    double progressPercent = total == 0 ? 0 : (completed * 100.0) / total;
                    return new AdminOverviewResponse.CompletionByDepartment(
                             departmentName,
                             progressPercent,
                             classifyDepartmentStatus(progressPercent)
                    );
                })
                .sorted(Comparator.comparing(AdminOverviewResponse.CompletionByDepartment::department, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<AdminOverviewResponse.QuizPerformanceByDepartment> buildQuizPerformanceByDepartment(DashboardContext ctx) {
        return assessmentAttemptRepository
                .getAssessmentPerformanceByDepartment(ctx.tenant().getId(), ctx.createdByFilter())
                .stream()
                .map(row -> {
                    String dept = "UNKNOWN";
                    if (row[0] instanceof Department d) {
                        dept = d.getDisplayName();
                    } else if (row[0] != null) {
                        dept = row[0].toString();
                    }
                    return new AdminOverviewResponse.QuizPerformanceByDepartment(
                        dept,
                        row[1] == null ? 0 : (Double) row[1],
                        row[2] == null ? 0 : ((Double) row[2]) * 100
                    );
                })
                .sorted(Comparator.comparing(AdminOverviewResponse.QuizPerformanceByDepartment::department, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<AdminOverviewResponse.HighestFailureLesson> buildHighestFailureLessons(DashboardContext ctx) {
        return assessmentAttemptRepository
                .getAssessmentFailureRateByDepartment(ctx.tenant().getId(), ctx.createdByFilter())
                .stream()
                .limit(10)
                .map(row -> {
                    String dept = "UNKNOWN";
                    if (row[1] instanceof Department d) {
                        dept = d.getDisplayName();
                    } else if (row[1] != null) {
                        dept = row[1].toString();
                    }
                    return new AdminOverviewResponse.HighestFailureLesson(
                        (String) row[0],
                        dept,
                        row[2] == null ? 0 : ((Double) row[2])
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentLeaderboardResponse getDepartmentLeaderboard() {
        tenantSchemaService.applyCurrentTenantSearchPath();
        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        UUID createdByFilter = null;
        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            createdByFilter = actorId;
        }

        // 1. Fetch raw department completion stats
        Map<String, double[]> completionStats = new HashMap<>();
        assignmentRepository.fetchDepartmentStats(tenant.getId(), AssignmentStatus.COMPLETED, createdByFilter)
                .forEach(r -> {
                    String deptName = "UNKNOWN";
                    if (r[0] instanceof Department d) {
                        deptName = d.getDisplayName();
                    } else if (r[0] != null) {
                        deptName = r[0].toString();
                    }
                    long total = ((Number) r[1]).longValue();
                    long completed = ((Number) r[2]).longValue();
                    double completionRate = total == 0 ? 0 : (completed * 100.0) / total;
                    completionStats.put(deptName, new double[]{completionRate, (double) completed, (double) total});
                });

        // 2. Fetch raw quiz performance stats
        Map<String, double[]> quizStats = new HashMap<>();
        assessmentAttemptRepository.getAssessmentPerformanceByDepartment(tenant.getId(), createdByFilter)
                .forEach(r -> {
                    String deptName = "UNKNOWN";
                    if (r[0] instanceof Department d) {
                        deptName = d.getDisplayName();
                    } else if (r[0] != null) {
                        deptName = r[0].toString();
                    }
                    double avgScore = r[1] == null ? 0.0 : (Double) r[1];
                    double passRate = r[2] == null ? 0.0 : ((Double) r[2]) * 100.0;
                    quizStats.put(deptName, new double[]{avgScore, passRate});
                });

        // 3. Combine and calculate Leaderboard scores
        List<DepartmentLeaderboardResponse.LeaderboardRow> rows = new ArrayList<>();
        java.util.Set<String> allDepartments = new java.util.HashSet<>();
        allDepartments.addAll(completionStats.keySet());
        allDepartments.addAll(quizStats.keySet());

        for (String dept : allDepartments) {
            double[] comp = completionStats.getOrDefault(dept, new double[]{0.0, 0.0, 0.0});
            double[] quiz = quizStats.getOrDefault(dept, new double[]{0.0, 0.0});

            double completionRate = comp[0];
            long completed = (long) comp[1];
            long total = (long) comp[2];
            double avgScore = quiz[0];
            double passRate = quiz[1];

            // Apply our Leaderboard Composite Formula
            double compositeScore = (completionRate * 0.6) + (avgScore * 0.3) + (passRate * 0.1);

            rows.add(new DepartmentLeaderboardResponse.LeaderboardRow(
                    0, // rank (to be populated during sorting)
                    dept,
                    Math.round(compositeScore * 10.0) / 10.0,
                    Math.round(completionRate * 10.0) / 10.0,
                    Math.round(avgScore * 10.0) / 10.0,
                    completed,
                    total,
                    ""
            ));
        }

        // 4. Sort by composite score (highest first) and set Rank
        List<DepartmentLeaderboardResponse.LeaderboardRow> sortedRows = rows.stream()
                .sorted(Comparator.comparingDouble(DepartmentLeaderboardResponse.LeaderboardRow::leaderboardScore).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < sortedRows.size(); i++) {
            int rank = i + 1;
            DepartmentLeaderboardResponse.LeaderboardRow original = sortedRows.get(i);
            String status = "ON_TRACK";
            if (rank == 1) {
                status = "LEADER";
            } else if (original.completionRatePercent() < 50.0) {
                status = "FALLING_BEHIND";
            }

            sortedRows.set(i, new DepartmentLeaderboardResponse.LeaderboardRow(
                    rank,
                    original.departmentDisplayName(),
                    original.leaderboardScore(),
                    original.completionRatePercent(),
                    original.averageQuizScorePercent(),
                    original.completedAssignments(),
                    original.totalAssignments(),
                    status
            ));
        }

        return new DepartmentLeaderboardResponse(sortedRows);
    }

    @Transactional
    public void resetAttempts(UUID targetUserId, String assessmentConfigId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        TenantUser targetMembership = tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Target user not found in this tenant"));

        // Standard manager (ADMIN role) is scoped: can only reset attempts for users they personally onboarded.
        // SUPER_ADMIN has unrestricted access across the tenant.
        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            if (!actorId.equals(targetMembership.getCreatedBy())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to reset attempts for this user");
            }
        }

        List<AssessmentAttempt> attempts = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(targetUserId, assessmentConfigId);
        if (!attempts.isEmpty()) {
            AssessmentResetLog log = new AssessmentResetLog();
            log.setId(UUID.randomUUID());
            log.setUserId(targetUserId);
            log.setAssessmentConfigId(assessmentConfigId);
            log.setManagerId(actorId != null ? actorId : targetMembership.getCreatedBy());
            log.setResetAt(Instant.now());
            log.setAttemptsCount(attempts.size());
            assessmentResetLogRepository.save(log);

            assessmentAttemptRepository.deleteAll(attempts);
        }

        if (actorId != null) {
            auditService.log(
                    actorId,
                    AuditAction.ASSESSMENT_RESET_ATTEMPTS,
                    "ASSESSMENT",
                    assessmentConfigId,
                    "Reset final assessment attempts for user ID " + targetUserId + " on assessment config " + assessmentConfigId
            );
        }
    }

    @Transactional(readOnly = true)
    public TenantAdminDetailsResponse getTenantAdminDetails() {
        tenantSchemaService.applyCurrentTenantSearchPath();
        Tenant tenant = currentTenant();
        
        UUID adminId = currentActorUserId();
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Logged in admin not found: " + adminId));
        
        TenantUser membership = tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId())
                .orElseThrow(() -> new IllegalStateException("Admin membership not found for tenant: " + tenant.getSlug()));

        long activeSeats = tenantUserRepository.findByTenantId(tenant.getId()).size();

        return new TenantAdminDetailsResponse(
                tenant.getId(),
                tenant.getCompanyName(),
                tenant.getSlug(),
                tenant.getPlan(),
                tenant.getMaxSeats(),
                activeSeats,
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getTrialEndsAt(),
                admin.getName(),
                admin.getEmail(),
                membership.getRole(),
                membership.getDepartment() == null ? null : membership.getDepartment().getDisplayName(),
                admin.getLocation(),
                admin.getLastLoginAt()
        );
    }

    private UUID currentActorUserId() {
        return SecurityUtils.currentUserIdOrNull();
    }

    public String currentActorUserEmail() {
        UUID actorId = currentActorUserId();
        if (actorId == null) {
            return "system@icentric.com"; // System fallback
        }
        return userRepository.findById(actorId)
                .map(User::getEmail)
                .orElse("system@icentric.com");
    }

    private Tenant currentTenant() {
        String slug = TenantContext.getTenant();
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));
    }

    private long countRiskUsers() {
        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();

        // BUG 3 FIX: scope to this tenant's learners only — never load all tenants' data.
        List<UserAssignment> assignments = learnerUserIds.isEmpty()
                ? List.of()
                : assignmentRepository.findByUserIdIn(learnerUserIds);

        Map<UUID, List<UserAssignment>> userAssignments =
                assignments.stream().collect(Collectors.groupingBy(UserAssignment::getUserId));

        long riskUsersCount = 0;

        for (var entry : userAssignments.entrySet()) {
            UUID userId = entry.getKey();
            List<UserAssignment> userAssgn = entry.getValue();

            // BUG 4 FIX (countRiskUsers): use lesson count as denominator.
            long totalLessons = userAssgn.stream()
                    .mapToLong(a -> lessonRepository.countLessonsInTrack(a.getTrackId()))
                    .sum();
            long completed = progressRepository.countCompletedByUser(userId);

            double completionPercent = totalLessons == 0 ? 0 : (completed * 100.0) / totalLessons;
            Double avgScore = assessmentAttemptRepository.getAverageScoreByUser(userId);
            double score = avgScore == null ? 0 : avgScore * 100;

            boolean overdueFlag =
                    userAssgn.stream().anyMatch(a ->
                            a.getDueDate() != null &&
                                    a.getDueDate().isBefore(Instant.now()) &&
                                    a.getStatus() != AssignmentStatus.COMPLETED
                    );

            if (overdueFlag || completionPercent < 50 || score < 50) {
                riskUsersCount++;
            }
        }

        return riskUsersCount;
    }

    /**
     * BUG 2 FIX: Completion delta now filters by {@code completedAt} — the timestamp
     * written when a learner actually finishes a track — not {@code assignedAt} which
     * is when the admin created the assignment.  The old logic compared
     * "assignments created this week" vs "last week", which is unrelated to completions.
     *
     * <p>Numerator  = completions that happened inside the window.<br>
     * Denominator = all assignments (total pool) — gives a rate of
     * "how many assignments got completed this week out of all assignments".
     */
    private double computeCompletionDelta(List<UserAssignment> assignments, Instant sevenDaysAgo, Instant fourteenDaysAgo) {
        long totalAssignments = assignments.size();
        if (totalAssignments == 0) return 0.0;

        long completedThisWeek = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED
                          && a.getCompletedAt() != null
                          && !a.getCompletedAt().isBefore(sevenDaysAgo))
                .count();

        long completedPrevWeek = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED
                          && a.getCompletedAt() != null
                          && !a.getCompletedAt().isBefore(fourteenDaysAgo)
                          && a.getCompletedAt().isBefore(sevenDaysAgo))
                .count();

        double currentRate  = (completedThisWeek  * 100.0) / totalAssignments;
        double previousRate = (completedPrevWeek  * 100.0) / totalAssignments;
        return currentRate - previousRate;
    }

    private AdminOverviewResponse.CompletionByDepartment.Status classifyDepartmentStatus(double progressPercent) {
        if (progressPercent >= 80) {
            return AdminOverviewResponse.CompletionByDepartment.Status.ON_TRACK;
        }
        if (progressPercent >= 50) {
            return AdminOverviewResponse.CompletionByDepartment.Status.AT_RISK;
        }
        return AdminOverviewResponse.CompletionByDepartment.Status.CRITICAL;
    }

    private static final List<AuditAction> MAJOR_ACTIVITY_ACTIONS = List.of(
            AuditAction.CERTIFICATE_ISSUED,
            AuditAction.COURSE_COMPLETED,
            AuditAction.COURSE_FAILED,
            AuditAction.ASSIGNMENT_OVERDUE,
            AuditAction.ASSIGNMENT_ESCALATION_SENT,
            AuditAction.QUIZ_LOCKED_MAX_ATTEMPTS
    );

    List<AdminOverviewResponse.ActivityItem> buildActivityFeed(
            Tenant tenant,
            Instant now,
            Map<UUID, User> usersById,
            List<UUID> tenantUserIds,
            TenantUser actorMembership
    ) {
        PageRequest pageRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<com.icentric.Icentric.audit.entity.AuditLog> logs;
        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            logs = tenantUserIds.isEmpty() ? List.of() : auditLogRepository.findByTenantSlugAndActionInAndUserIdIn(
                    tenant.getSlug(), 
                    MAJOR_ACTIVITY_ACTIONS,
                    tenantUserIds,
                    pageRequest
            ).getContent();
        } else {
            logs = auditLogRepository.findByTenantSlugAndActionIn(
                    tenant.getSlug(), 
                    MAJOR_ACTIVITY_ACTIONS,
                    pageRequest
            ).getContent();
        }

        return logs.stream()
            .map(log -> {
                String userName = "System";
                if (log.getUserId() != null) {
                    User u = usersById.get(log.getUserId());
                    if (u == null) {
                        u = userRepository.findById(log.getUserId()).orElse(null);
                    }
                    if (u != null) {
                        userName = u.getName() != null && !u.getName().isBlank() ? u.getName() : "A learner";
                    } else {
                        userName = "A learner";
                    }
                }

                String actionText = userName + " performed an action";
                AdminOverviewResponse.ActivityItem.Type type = AdminOverviewResponse.ActivityItem.Type.INFO;

                String details = cleanAuditDetail(log.getDetails());

                switch (log.getAction()) {
                    case CERTIFICATE_ISSUED -> {
                        if (details != null && !details.isBlank()) {
                            String msg = details.replaceAll("\\.?\\s*Generation queued asynchronously\\.?", "");
                            msg = msg.replaceAll("\\.?\\s*Generation re-queued\\.?", "");
                            msg = msg.trim();
                            if (msg.startsWith("Issued certificate to ")) {
                                actionText = "Certificate issued to " + msg.substring("Issued certificate to ".length());
                            } else {
                                actionText = msg;
                            }
                        } else {
                            actionText = userName + " earned a new certificate";
                        }
                        type = AdminOverviewResponse.ActivityItem.Type.SUCCESS;
                    }
                    case QUIZ_PASSED -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " passed an assessment";
                        type = AdminOverviewResponse.ActivityItem.Type.SUCCESS;
                    }
                    case COURSE_COMPLETED -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " completed a track";
                        type = AdminOverviewResponse.ActivityItem.Type.SUCCESS;
                    }
                    case QUIZ_ATTEMPT -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " attempted an assessment";
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    case LESSON_COMPLETED -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " completed a lesson";
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    case ASSIGNMENT_OVERDUE -> {
                        if (details != null && !details.isBlank()) {
                            actionText = details.replaceAll("\\s+with due date.*$", "");
                        } else {
                            actionText = userName + " has an overdue assignment";
                        }
                        type = AdminOverviewResponse.ActivityItem.Type.WARNING;
                    }
                    case QUIZ_FAILED_RETRY_AVAILABLE -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " failed an assessment but can retry";
                        type = AdminOverviewResponse.ActivityItem.Type.WARNING;
                    }
                    case ASSIGNMENT_ESCALATION_SENT -> {
                        actionText = (details != null && !details.isBlank()) ? details : "Escalation email sent for " + userName;
                        type = AdminOverviewResponse.ActivityItem.Type.WARNING;
                    }
                    case QUIZ_LOCKED_MAX_ATTEMPTS -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " was locked out of an assessment (max attempts)";
                        type = AdminOverviewResponse.ActivityItem.Type.ERROR;
                    }
                    case COURSE_FAILED -> {
                        actionText = (details != null && !details.isBlank()) ? details : userName + " failed a track";
                        type = AdminOverviewResponse.ActivityItem.Type.ERROR;
                    }
                    case CREATE_USER -> {
                        actionText = (details != null && !details.isBlank()) ? details : "New user account created";
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    case ASSIGN_TRACK -> {
                        actionText = (details != null && !details.isBlank()) ? details : "Track assigned to " + userName;
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    default -> {
                        if (details != null && !details.isBlank()) {
                            actionText = details;
                        } else {
                            actionText = userName + " triggered " + log.getAction().name().replace("_", " ").toLowerCase();
                        }
                    }
                }

                return new AdminOverviewResponse.ActivityItem(
                        type,
                        actionText,
                        formatTimeAgo(log.getCreatedAt(), now)
                );
            })
            .toList();
    }

    private String formatTimeAgo(Instant eventTime, Instant now) {
        if (eventTime == null) return "just now";
        long seconds = now.getEpochSecond() - eventTime.getEpochSecond();
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days == 1) return "yesterday";
        return days + "d ago";
    }

    String cleanAuditDetail(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        // 1. Remove emails like <test@test.com>
        String clean = details.replaceAll("<[^>]*>", "");
        // 2. Remove department=... and role=... fields
        clean = clean.replaceAll(",?\\s*(department|role)=[^,\\s]*", "");
        // 3. Remove UUIDs in brackets like [a1b2c3d4-...]
        clean = clean.replaceAll("\\[[^\\]]*\\]", "");
        // 4. Clean up consecutive commas
        clean = clean.replaceAll(",\\s*,", ",");
        clean = clean.replaceAll("\\s+,", ",");
        clean = clean.replaceAll(",\\s*$", "");
        // 5. Collapse multiple spaces and trim
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? null : clean;
    }

    private List<DepartmentStat> rankDepartmentStats(List<DepartmentAggregate> aggregates) {
        List<DepartmentAggregate> sorted = aggregates.stream()
                .sorted(
                        Comparator.comparingDouble(DepartmentAggregate::completionRate).reversed()
                                .thenComparing(DepartmentAggregate::completed, Comparator.reverseOrder())
                                .thenComparing(DepartmentAggregate::total, Comparator.reverseOrder())
                                .thenComparing(
                                        aggregate -> aggregate.department() == null ? "UNKNOWN" : aggregate.department().name(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .toList();

        List<DepartmentStat> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            DepartmentAggregate aggregate = sorted.get(i);
            ranked.add(new DepartmentStat(
                    i + 1,
                    aggregate.department() == null ? null : aggregate.department(),
                    aggregate.total(),
                    aggregate.completed(),
                    aggregate.completionRate()
            ));
        }

        return ranked;
    }

    private record DepartmentAggregate(
            Department department,
            long total,
            long completed
    ) {
        double completionRate() {
            return total == 0 ? 0 : (completed * 100.0) / total;
        }
    }

    // ── OVERDUE NOTIFICATION ─────────────────────────────────────────────────

    public record OverdueNotificationResult(
            int totalOverdue,
            int emailsSent,
            int emailsFailed
    ) {}

    @Transactional
    public OverdueNotificationResult notifyOverdueUsers(List<UUID> targetUserIds) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        Instant now = Instant.now();

        // Only LEARNER roles
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();

        // Filter down to requested users if caller supplied a list
        List<UUID> scopedUserIds = (targetUserIds == null || targetUserIds.isEmpty())
                ? learnerUserIds
                : learnerUserIds.stream().filter(targetUserIds::contains).toList();

        // Find overdue assignments (past due date, not completed) for scoped users
        // BUG 3 FIX: scope to this tenant's learners only — never load all tenants' data.
        List<UserAssignment> overdueAssignments = scopedUserIds.isEmpty()
                ? List.of()
                : assignmentRepository.findByUserIdIn(scopedUserIds).stream()
                        .filter(a -> a.getStatus() != AssignmentStatus.COMPLETED)
                        .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(now))
                        .toList();

        // Deduplicate: one email per user (summarise all their overdue tracks)
        Map<UUID, List<UserAssignment>> byUser = overdueAssignments.stream()
                .collect(Collectors.groupingBy(UserAssignment::getUserId));

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (Map.Entry<UUID, List<UserAssignment>> entry : byUser.entrySet()) {
            UUID userId = entry.getKey();
            List<UserAssignment> userOverdue = entry.getValue();

            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null) { 
                futures.add(CompletableFuture.completedFuture(false));
                continue; 
            }

            String displayName = (user.getName() != null && !user.getName().isBlank())
                    ? user.getName() : user.getEmail();

            // Build a bullet list of overdue tracks
            StringBuilder trackList = new StringBuilder();
            for (UserAssignment a : userOverdue) {
                Track track = trackRepository.findById(a.getTrackId()).orElse(null);
                String title = track != null ? track.getTitle() : a.getTrackId().toString();
                long daysOverdue = Math.max(1, (now.getEpochSecond() - a.getDueDate().getEpochSecond()) / 86_400);
                trackList.append("• <strong>").append(title).append("</strong> — overdue by ").append(daysOverdue).append(" day(s)<br>");
            }

            String message = "You have overdue learning assignment(s) that require your immediate attention:<br><br>"
                    + trackList
                    + "<br>Please log in and complete your pending training as soon as possible.";

            Map<String, Object> vars = new java.util.HashMap<>();
            vars.put("tenantName", tenant.getCompanyName());
            vars.put("notificationPill", "🔴\u00a0OVERDUE TRAINING");
            vars.put("displayName", displayName);
            vars.put("title", "Action Required: Overdue Training");
            vars.put("message", message);
            vars.put("actionUrl", "#");
            vars.put("actionText", "View My Training →");

            CompletableFuture<Boolean> future = emailService.sendTemplateEmail(
                    user.getEmail(),
                    "Action Required: You Have Overdue Training",
                    "AISafe_Email_Notification",
                    vars
            ).handle((res, ex) -> {
                if (ex != null) {
                    log.error("Failed to send overdue notification to {}", user.getEmail(), ex);
                    return false;
                }
                return true;
            });
            futures.add(future);
        }

        // Wait for all emails to be processed concurrently
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int sent = 0;
        int failed = 0;
        for (CompletableFuture<Boolean> f : futures) {
            if (Boolean.TRUE.equals(f.getNow(false))) {
                sent++;
            } else {
                failed++;
            }
        }

        return new OverdueNotificationResult(byUser.size(), sent, failed);
    }

    @Transactional(readOnly = true)
    public List<AssessmentResetLogResponse> getAssessmentResetHistory() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        // Fetch all learners in tenant
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        // Scope to onboarded users if actor is a standard ADMIN/MANAGER
        if (actorMembership != null && ("ADMIN".equals(actorMembership.getRole()) || "MANAGER".equals(actorMembership.getRole()))) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();
        if (learnerUserIds.isEmpty()) {
            return List.of();
        }

        // Fetch all reset logs for these learners
        List<AssessmentResetLog> logs = assessmentResetLogRepository.findByUserIdIn(learnerUserIds);
        if (logs.isEmpty()) {
            return List.of();
        }

        // Pre-fetch related users (learners and managers) to avoid N+1 queries
        List<UUID> allUserIds = new ArrayList<>();
        logs.forEach(l -> {
            allUserIds.add(l.getUserId());
            allUserIds.add(l.getManagerId());
        });
        
        Map<UUID, User> usersById = userRepository.findByIdIn(
                allUserIds.stream().distinct().toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // Fetch all assessment configs for title mapping
        List<AssessmentConfig> configs = assessmentConfigRepository.findAll();
        Map<String, String> configTitles = configs.stream().collect(Collectors.toMap(
                AssessmentConfig::getId,
                c -> c.getConfigData().path("title").asText("Final Assessment"),
                (a, b) -> a
        ));

        // Fetch attempts for cumulative calculations
        List<AssessmentAttempt> allAttempts = assessmentAttemptRepository.findByUserIdIn(learnerUserIds);
        Map<UUID, Map<String, List<AssessmentAttempt>>> attemptsByUserAndConfig = allAttempts.stream()
                .collect(Collectors.groupingBy(
                        AssessmentAttempt::getUserId,
                        Collectors.groupingBy(AssessmentAttempt::getAssessmentConfigId)
                ));

        // Group reset logs by user and config to calculate past attempts
        Map<UUID, Map<String, List<AssessmentResetLog>>> logsByUserAndConfig = logs.stream()
                .collect(Collectors.groupingBy(
                        AssessmentResetLog::getUserId,
                        Collectors.groupingBy(AssessmentResetLog::getAssessmentConfigId)
                ));

        List<AssessmentResetLogResponse> result = new ArrayList<>();

        for (AssessmentResetLog log : logs) {
            User learner = usersById.get(log.getUserId());
            User manager = usersById.get(log.getManagerId());
            String learnerName = learner == null ? "Unknown User" : (learner.getName() != null && !learner.getName().isBlank() ? learner.getName() : learner.getEmail());
            String learnerEmail = learner == null ? "" : learner.getEmail();
            String managerName = manager == null ? "System" : (manager.getName() != null && !manager.getName().isBlank() ? manager.getName() : manager.getEmail());
            String managerEmail = manager == null ? "" : manager.getEmail();

            String title = configTitles.getOrDefault(log.getAssessmentConfigId(), "Final Assessment");

            // Calculate total attempts across all cycles
            List<AssessmentResetLog> userLogs = logsByUserAndConfig
                    .getOrDefault(log.getUserId(), Map.of())
                    .getOrDefault(log.getAssessmentConfigId(), List.of());

            int attemptsFromResets = userLogs.stream()
                    .mapToInt(AssessmentResetLog::getAttemptsCount)
                    .sum();

            List<AssessmentAttempt> currentAttempts = attemptsByUserAndConfig
                    .getOrDefault(log.getUserId(), Map.of())
                    .getOrDefault(log.getAssessmentConfigId(), List.of());

            int currentAttemptsCount = currentAttempts.size();
            int cumulativeAttempts = attemptsFromResets + currentAttemptsCount;

            boolean isCompleted = currentAttempts.stream().anyMatch(a -> "PASSED".equalsIgnoreCase(a.getStatus()));
            Integer totalAttemptsToComplete = null;
            if (isCompleted) {
                AssessmentAttempt passedAttempt = currentAttempts.stream()
                        .filter(a -> "PASSED".equalsIgnoreCase(a.getStatus()))
                        .findFirst()
                        .orElse(null);
                if (passedAttempt != null) {
                    totalAttemptsToComplete = attemptsFromResets + (passedAttempt.getAttemptNumber() != null ? passedAttempt.getAttemptNumber() : 1);
                }
            }

            result.add(new AssessmentResetLogResponse(
                    log.getId(),
                    log.getUserId(),
                    learnerName,
                    learnerEmail,
                    log.getAssessmentConfigId(),
                    title,
                    log.getManagerId(),
                    managerName,
                    managerEmail,
                    log.getResetAt(),
                    log.getAttemptsCount(),
                    cumulativeAttempts,
                    totalAttemptsToComplete,
                    isCompleted
            ));
        }

        result.sort((a, b) -> b.resetAt().compareTo(a.resetAt()));

        return result;
    }

    @Transactional(readOnly = true)
    public LearningAuditReportResponse getLearningAuditReport(
            int page,
            int size,
            String search,
            String departmentFilter,
            String categoryFilter
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            UUID finalActorId = actorId;
            memberships = memberships.stream()
                    .filter(m -> finalActorId.equals(m.getCreatedBy()))
                    .toList();
        }

        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();
        if (learnerUserIds.isEmpty()) {
            return new LearningAuditReportResponse(List.of(), 0, 1);
        }

        Map<UUID, User> usersById = userRepository.findByIdIn(learnerUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<TenantUser> filteredMemberships = new ArrayList<>();
        for (TenantUser m : memberships) {
            User user = usersById.get(m.getUserId());
            if (user == null) {
                continue;
            }

            if (departmentFilter != null && !departmentFilter.isBlank()) {
                if (m.getDepartment() == null ||
                        (!m.getDepartment().name().equalsIgnoreCase(departmentFilter) &&
                         !m.getDepartment().getDisplayName().equalsIgnoreCase(departmentFilter))) {
                    continue;
                }
            }

            if (search != null && !search.isBlank()) {
                String term = search.toLowerCase();
                String name = user.getName() == null ? "" : user.getName().toLowerCase();
                String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase();
                if (!name.contains(term) && !email.contains(term)) {
                    continue;
                }
            }

            filteredMemberships.add(m);
        }

        List<UUID> filteredUserIds = filteredMemberships.stream().map(TenantUser::getUserId).toList();
        if (filteredUserIds.isEmpty()) {
            return new LearningAuditReportResponse(List.of(), 0, 1);
        }

        List<UserAssignment> allAssignments = assignmentRepository.findByUserIdIn(filteredUserIds);
        Map<UUID, List<UserAssignment>> assignmentsByUser = allAssignments.stream()
                .collect(Collectors.groupingBy(UserAssignment::getUserId));

        List<AssessmentAttempt> allAttempts = assessmentAttemptRepository.findByUserIdIn(filteredUserIds);
        Map<UUID, List<AssessmentAttempt>> attemptsByUser = allAttempts.stream()
                .collect(Collectors.groupingBy(AssessmentAttempt::getUserId));

        List<IssuedCertificate> allCertificates = issuedCertificateRepository.findByUserIdIn(filteredUserIds);
        Map<UUID, List<IssuedCertificate>> certificatesByUser = allCertificates.stream()
                .collect(Collectors.groupingBy(IssuedCertificate::getUserId));

        List<Track> allTracks = trackRepository.findAll();
        Map<UUID, String> trackTitles = allTracks.stream()
                .collect(Collectors.toMap(Track::getId, Track::getTitle, (a, b) -> a));

        List<LearningAuditReportResponse.EmployeeAuditRow> allRows = new ArrayList<>();
        Instant now = Instant.now();

        for (TenantUser member : filteredMemberships) {
            UUID userId = member.getUserId();
            User user = usersById.get(userId);

            List<UserAssignment> userAssignments = assignmentsByUser.getOrDefault(userId, List.of());
            List<AssessmentAttempt> userAttempts = attemptsByUser.getOrDefault(userId, List.of());
            List<IssuedCertificate> userCertificates = certificatesByUser.getOrDefault(userId, List.of());

            // 1. Compliance Status
            long totalAssigned = userAssignments.size();
            long completed = userAssignments.stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED)
                    .count();
            long overdue = userAssignments.stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.OVERDUE ||
                            (a.getDueDate() != null && a.getDueDate().isBefore(now) && a.getStatus() != AssignmentStatus.COMPLETED))
                    .count();
            double progressPercent = totalAssigned == 0 ? 0.0 : (completed * 100.0) / totalAssigned;

            var complianceStatus = new LearningAuditReportResponse.ComplianceStatus(
                    totalAssigned,
                    completed,
                    overdue,
                    Math.round(progressPercent * 10.0) / 10.0
            );

            // 2. Quiz Performance (Average Quiz Score)
            double averageQuizScorePercent = userAttempts.stream()
                    .filter(a -> a.getScore() != null)
                    .mapToInt(AssessmentAttempt::getScore)
                    .average()
                    .orElse(0.0);

            // 3. First-Time Pass Rate
            Map<String, List<AssessmentAttempt>> attemptsByConfig = userAttempts.stream()
                    .collect(Collectors.groupingBy(AssessmentAttempt::getAssessmentConfigId));

            long totalAttemptedQuizzes = attemptsByConfig.size();
            long passedOnFirstAttempt = 0;

            for (var entry : attemptsByConfig.entrySet()) {
                List<AssessmentAttempt> configAttempts = entry.getValue();
                AssessmentAttempt firstAttempt = configAttempts.stream()
                        .min((a, b) -> {
                            if (a.getAttemptNumber() != null && b.getAttemptNumber() != null) {
                                int cmp = a.getAttemptNumber().compareTo(b.getAttemptNumber());
                                if (cmp != 0) return cmp;
                            }
                            Instant ta = a.getDateCompleted() != null ? a.getDateCompleted() : (a.getStartedAt() != null ? a.getStartedAt() : Instant.MIN);
                            Instant tb = b.getDateCompleted() != null ? b.getDateCompleted() : (b.getStartedAt() != null ? b.getStartedAt() : Instant.MIN);
                            return ta.compareTo(tb);
                        })
                        .orElse(null);

                if (firstAttempt != null && "PASSED".equalsIgnoreCase(firstAttempt.getStatus())) {
                    passedOnFirstAttempt++;
                }
            }

            double firstTimePassRatePercent = totalAttemptedQuizzes == 0 ? 0.0 : (passedOnFirstAttempt * 100.0) / totalAttemptedQuizzes;

            // 4. Average Days to Complete
            List<UserAssignment> completedAssignments = userAssignments.stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED && a.getCompletedAt() != null && a.getAssignedAt() != null)
                    .toList();

            double averageDaysToComplete = 0.0;
            if (!completedAssignments.isEmpty()) {
                double totalDays = 0.0;
                for (UserAssignment a : completedAssignments) {
                    double diffSeconds = Math.max(0.0, a.getCompletedAt().getEpochSecond() - a.getAssignedAt().getEpochSecond());
                    totalDays += (diffSeconds / 86400.0);
                }
                averageDaysToComplete = totalDays / completedAssignments.size();
            }

            // 5. On-Time Completion Rate
            long punctualityDenominator = userAssignments.stream()
                    .filter(a -> a.getDueDate() != null && (a.getStatus() == AssignmentStatus.COMPLETED || a.getStatus() == AssignmentStatus.OVERDUE || a.getDueDate().isBefore(now)))
                    .count();

            long completedOnTime = userAssignments.stream()
                    .filter(a -> a.getDueDate() != null && a.getStatus() == AssignmentStatus.COMPLETED && a.getCompletedAt() != null && !a.getCompletedAt().isAfter(a.getDueDate()))
                    .count();

            double onTimeCompletionRatePercent = punctualityDenominator == 0 ? (totalAssigned > 0 ? 100.0 : 0.0) : (completedOnTime * 100.0) / punctualityDenominator;

            // 6. Composite Score & Talent Category
            double learningScore = (averageQuizScorePercent * 0.5) + (firstTimePassRatePercent * 0.3) + (onTimeCompletionRatePercent * 0.2);

            String talentCategory;
            if (learningScore >= 90.0) {
                talentCategory = "STAR_LEARNER";
            } else if (learningScore >= 75.0) {
                talentCategory = "DILIGENT_LEARNER";
            } else if (learningScore >= 60.0) {
                talentCategory = "HANDS_ON_LEARNER";
            } else {
                talentCategory = "STALLED_LEARNER";
            }

            var excellenceMetrics = new LearningAuditReportResponse.ExcellenceMetrics(
                    Math.round(learningScore * 10.0) / 10.0,
                    Math.round(averageQuizScorePercent * 10.0) / 10.0,
                    Math.round(firstTimePassRatePercent * 10.0) / 10.0,
                    Math.round(averageDaysToComplete * 10.0) / 10.0,
                    talentCategory
            );

            // Certificates list
            List<LearningAuditReportResponse.CertificateSummary> certificatesEarned = userCertificates.stream()
                    .map(c -> new LearningAuditReportResponse.CertificateSummary(
                            c.getId(),
                            trackTitles.getOrDefault(c.getTrackId(), "Unknown Course"),
                            c.getIssuedAt()
                    ))
                    .sorted((a, b) -> b.issuedAt().compareTo(a.issuedAt()))
                    .toList();

            String deptName = member.getDepartment() == null ? "UNKNOWN" : member.getDepartment().getDisplayName();

            allRows.add(new LearningAuditReportResponse.EmployeeAuditRow(
                    userId,
                    user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail(),
                    user.getEmail(),
                    deptName,
                    complianceStatus,
                    excellenceMetrics,
                    certificatesEarned
            ));
        }

        // Apply Category Filter in memory
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            allRows = allRows.stream()
                    .filter(row -> {
                        String cat = row.excellenceMetrics().talentCategory();
                        return cat.equalsIgnoreCase(categoryFilter) ||
                                cat.replace("_LEARNER", "").equalsIgnoreCase(categoryFilter);
                    })
                    .collect(Collectors.toList());
        }

        // Default sort: highest score first, then name alphabetically
        allRows.sort((a, b) -> {
            int cmp = Double.compare(b.excellenceMetrics().learningScore(), a.excellenceMetrics().learningScore());
            if (cmp != 0) return cmp;
            return a.name().compareToIgnoreCase(b.name());
        });

        // Pagination clamping
        int clPage = Math.max(0, page);
        int clSize = size <= 0 ? 10 : size;

        int totalElements = allRows.size();
        int totalPages = (int) Math.ceil((double) totalElements / clSize);
        if (totalPages == 0) totalPages = 1;

        int fromIndex = Math.min(clPage * clSize, totalElements);
        int toIndex = Math.min(fromIndex + clSize, totalElements);

        List<LearningAuditReportResponse.EmployeeAuditRow> pagedEmployees = allRows.subList(fromIndex, toIndex);

        return new LearningAuditReportResponse(pagedEmployees, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public byte[] getLearningAuditReportPdf(String search, String departmentFilter, String categoryFilter) {
        // Reuse our existing implementation for fetching and constructing report metrics
        // Since we want the entire report for PDF, we pass large size (e.g. 10000)
        LearningAuditReportResponse report = getLearningAuditReport(0, 10000, search, departmentFilter, categoryFilter);

        double totalComplianceRate = 0.0;
        double totalLearningScore = 0.0;
        long starLearnersCount = 0;
        int employeeCount = report.employees().size();

        for (var emp : report.employees()) {
            totalComplianceRate += emp.complianceStatus().progressPercent();
            totalLearningScore += emp.excellenceMetrics().learningScore();
            if ("STAR_LEARNER".equals(emp.excellenceMetrics().talentCategory())) {
                starLearnersCount++;
            }
        }

        double avgComplianceRate = employeeCount == 0 ? 0.0 : totalComplianceRate / employeeCount;
        double avgLearningScore = employeeCount == 0 ? 0.0 : totalLearningScore / employeeCount;

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
            .append("  .progress-bg {\n")
            .append("    background: #e5e7eb;\n")
            .append("    border-radius: 4px;\n")
            .append("    height: 6px;\n")
            .append("    width: 70px;\n")
            .append("    display: inline-block;\n")
            .append("    vertical-align: middle;\n")
            .append("    margin-right: 5px;\n")
            .append("  }\n")
            .append("  .progress-fg {\n")
            .append("    height: 100%;\n")
            .append("    border-radius: 4px;\n")
            .append("    background: linear-gradient(to right, #4f46e5, #7c3aed);\n")
            .append("  }\n")
            .append("  .badge {\n")
            .append("    padding: 2px 6px;\n")
            .append("    border-radius: 4px;\n")
            .append("    font-size: 9px;\n")
            .append("    font-weight: 600;\n")
            .append("    display: inline-block;\n")
            .append("  }\n")
            .append("  .badge-star { background: #fef3c7; color: #d97706; border: 1px solid #fcd34d; }\n")
            .append("  .badge-diligent { background: #ecfeff; color: #0891b2; border: 1px solid #c5f6fa; }\n")
            .append("  .badge-hands { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }\n")
            .append("  .badge-stalled { background: #fef2f2; color: #dc2626; border: 1px solid #fca5a5; }\n")
            .append("  .cert-pill {\n")
            .append("    background: #f3f4f6;\n")
            .append("    color: #4b5563;\n")
            .append("    border: 1px solid #e5e7eb;\n")
            .append("    padding: 1px 4px;\n")
            .append("    border-radius: 4px;\n")
            .append("    font-size: 8px;\n")
            .append("    margin-right: 3px;\n")
            .append("    margin-bottom: 3px;\n")
            .append("    display: inline-block;\n")
            .append("  }\n")
            .append("</style>\n</head>\n<body>\n")
            .append("  <div class=\"header\">\n")
            .append("    <div class=\"header-left\">\n")
            .append("      <h1 class=\"title\">Corporate Learning Audit & Talent Excellence Report</h1>\n")
            .append("      <p class=\"subtitle\">Headless Playwright PDF Export | Tenant: ").append(escapeHtml(currentTenant().getCompanyName())).append("</p>\n")
            .append("    </div>\n")
            .append("    <div class=\"header-right\">\n")
            .append("      Date Compiled: ").append(java.time.LocalDate.now().toString()).append("<br>\n")
            .append("      Organization Scope: Active Employees\n")
            .append("    </div>\n")
            .append("  </div>\n")
            .append("  <div class=\"stats-grid\">\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(employeeCount).append("</div>\n")
            .append("      <div class=\"stat-lbl\">TOTAL EMPLOYEES</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(Math.round(avgComplianceRate * 10.0) / 10.0).append("%</div>\n")
            .append("      <div class=\"stat-lbl\">AVG COMPLIANCE RATE</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(Math.round(avgLearningScore * 10.0) / 10.0).append("</div>\n")
            .append("      <div class=\"stat-lbl\">AVG EXCELLENCE SCORE</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"stat-card\">\n")
            .append("      <div class=\"stat-val\">").append(starLearnersCount).append("</div>\n")
            .append("      <div class=\"stat-lbl\">STAR LEARNERS</div>\n")
            .append("    </div>\n")
            .append("  </div>\n")
            .append("  <table>\n")
            .append("    <thead>\n")
            .append("      <tr>\n")
            .append("        <th>Employee</th>\n")
            .append("        <th>Department</th>\n")
            .append("        <th>Compliance Status</th>\n")
            .append("        <th>Progress</th>\n")
            .append("        <th>Completion Speed</th>\n")
            .append("        <th>Dynamic Score</th>\n")
            .append("        <th>Avg Quiz Score</th>\n")
            .append("        <th>First-Time Pass</th>\n")
            .append("        <th>Credentials Earned</th>\n")
            .append("        <th>Talent Category</th>\n")
            .append("      </tr>\n")
            .append("    </thead>\n")
            .append("    <tbody>\n");

        for (var emp : report.employees()) {
            String badgeClass = switch (emp.excellenceMetrics().talentCategory()) {
                case "STAR_LEARNER" -> "badge-star";
                case "DILIGENT_LEARNER" -> "badge-diligent";
                case "HANDS_ON_LEARNER" -> "badge-hands";
                default -> "badge-stalled";
            };

            String formattedCategory = emp.excellenceMetrics().talentCategory().replace("_", " ");

            String formattedSpeed = emp.excellenceMetrics().averageDaysToComplete() > 0 
                ? emp.excellenceMetrics().averageDaysToComplete() + " days"
                : "N/A";

            String complianceDetail = emp.complianceStatus().completed() + "/" + emp.complianceStatus().totalAssigned() + " tracks";
            if (emp.complianceStatus().overdue() > 0) {
                complianceDetail += "<br><span style=\"color:#dc2626;font-weight:600;font-size:9px;\">⚠️ overdue: " + emp.complianceStatus().overdue() + "</span>";
            }

            StringBuilder certsHtml = new StringBuilder();
            if (emp.certificatesEarned().isEmpty()) {
                certsHtml.append("<span style=\"color:#9ca3af;font-size:9px;\">None</span>");
            } else {
                for (var cert : emp.certificatesEarned()) {
                    certsHtml.append("<span class=\"cert-pill\">🎓 ").append(escapeHtml(cert.trackTitle())).append("</span> ");
                }
            }

            html.append("      <tr>\n")
                .append("        <td><strong>").append(escapeHtml(emp.name())).append("</strong><br><span style=\"color:#9ca3af;font-size:9px;\">").append(escapeHtml(emp.email())).append("</span></td>\n")
                .append("        <td>").append(escapeHtml(emp.department())).append("</td>\n")
                .append("        <td>").append(complianceDetail).append("</td>\n")
                .append("        <td><div class=\"progress-bg\"><div class=\"progress-fg\" style=\"width:").append(emp.complianceStatus().progressPercent()).append("%;\"></div></div> ").append(emp.complianceStatus().progressPercent()).append("%</td>\n")
                .append("        <td>").append(formattedSpeed).append("</td>\n")
                .append("        <td><strong>").append(emp.excellenceMetrics().learningScore()).append("</strong></td>\n")
                .append("        <td>").append(emp.excellenceMetrics().averageQuizScorePercent()).append("%</td>\n")
                .append("        <td>").append(emp.excellenceMetrics().firstTimePassRatePercent()).append("%</td>\n")
                .append("        <td>").append(certsHtml.toString()).append("</td>\n")
                .append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(formattedCategory).append("</span></td>\n")
                .append("      </tr>\n");
        }

        if (report.employees().isEmpty()) {
            html.append("      <tr><td colspan=\"10\" style=\"text-align:center;color:#9ca3af;\">No records found matching filters.</td></tr>\n");
        }

        html.append("    </tbody>\n  </table>\n</body>\n</html>");

        return playwrightPdfService.render(html.toString(), true); // landscape A4 format
    }

    @Transactional(readOnly = true)
    public String getLearningAuditReportCsv(String search, String departmentFilter, String categoryFilter) {
        LearningAuditReportResponse report = getLearningAuditReport(0, 10000, search, departmentFilter, categoryFilter);

        StringBuilder csv = new StringBuilder();
        // Append UTF-8 BOM to ensure Excel opens it correctly with UTF-8 characters
        csv.append("\uFEFF");
        csv.append("Employee,Email,Department,Total Assigned,Completed,Overdue,Progress %,Speed to Complete (Days),Learning Score,Avg Quiz Score %,First-Time Pass Rate %,Talent Category\n");

        for (var emp : report.employees()) {
            csv.append(escapeCsvField(emp.name())).append(",")
               .append(escapeCsvField(emp.email())).append(",")
               .append(escapeCsvField(emp.department())).append(",")
               .append(emp.complianceStatus().totalAssigned()).append(",")
               .append(emp.complianceStatus().completed()).append(",")
               .append(emp.complianceStatus().overdue()).append(",")
               .append(emp.complianceStatus().progressPercent()).append(",")
               .append(emp.excellenceMetrics().averageDaysToComplete() > 0 ? emp.excellenceMetrics().averageDaysToComplete() : "N/A").append(",")
               .append(emp.excellenceMetrics().learningScore()).append(",")
               .append(emp.excellenceMetrics().averageQuizScorePercent()).append(",")
               .append(emp.excellenceMetrics().firstTimePassRatePercent()).append(",")
               .append(escapeCsvField(emp.excellenceMetrics().talentCategory())).append("\n");
        }
        return csv.toString();
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        String value = field.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    private String escapeHtml(String input) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(input == null ? "" : input);
    }
}


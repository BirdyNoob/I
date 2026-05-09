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
import com.icentric.Icentric.audit.constants.AuditAction;
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
            AuditLogRepository auditLogRepository
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
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getOverview() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();
        long totalUsers = memberships.size();
        
        List<UUID> learnerUserIds = memberships.stream().map(TenantUser::getUserId).toList();

        List<UserAssignment> assignments = assignmentRepository.findAll().stream()
                .filter(a -> learnerUserIds.contains(a.getUserId()))
                .toList();

        long totalAssignments = assignments.size();

        long completedAssignments = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.COMPLETED)
                .count();

        double completionRate =
                totalAssignments == 0 ? 0 :
                        (completedAssignments * 100.0) / totalAssignments;

        Double avgScore = assessmentAttemptRepository.getAverageScore();

        return new AdminAnalyticsResponse(
                totalUsers,
                totalAssignments,
                completedAssignments,
                completionRate,
                avgScore == null ? 0 : avgScore * 100
        );
    }

    @Transactional(readOnly = true)
    public List<RiskUserResponse> getRiskUsers() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        List<UUID> learnerUserIds = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .map(TenantUser::getUserId)
                .toList();

        List<UserAssignment> assignments = assignmentRepository.findAll().stream()
                .filter(a -> learnerUserIds.contains(a.getUserId()))
                .toList();

        Map<UUID, List<UserAssignment>> userAssignments =
                assignments.stream().collect(Collectors.groupingBy(UserAssignment::getUserId));

        List<RiskUserResponse> result = new ArrayList<>();

        for (var entry : userAssignments.entrySet()) {

            UUID userId = entry.getKey();
            List<UserAssignment> userAssgn = entry.getValue();

            var user = userRepository.findById(userId).orElseThrow();

            long completed =
                    progressRepository.countCompletedByUser(userId);

            long total = userAssgn.size();

            double completionPercent =
                    total == 0 ? 0 : (completed * 100.0) / total;

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

        List<Object[]> stats = assessmentAttemptRepository.getAssessmentStats();

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
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();

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

                long completed =
                        progressRepository.countCompletedByUser(userId);

                totalCompleted += completed;

                totalAssignments += assignmentRepository
                        .findByUserId(userId)
                        .size();

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
        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minusSeconds(7L * 24 * 60 * 60);
        Instant fourteenDaysAgo = now.minusSeconds(14L * 24 * 60 * 60);

        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .toList();
        Map<UUID, TenantUser> membershipByUserId = memberships.stream()
                .collect(Collectors.toMap(TenantUser::getUserId, m -> m, (a, b) -> a));
        List<UUID> tenantUserIds = memberships.stream().map(TenantUser::getUserId).distinct().toList();
        Map<UUID, User> usersById = userRepository.findByIdIn(tenantUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        List<UserAssignment> assignments = assignmentRepository.findAll().stream()
                .filter(a -> membershipByUserId.containsKey(a.getUserId()))
                .toList();
        long totalAssignments = assignments.size();
        long completedAssignments = assignments.stream().filter(a -> a.getStatus() == AssignmentStatus.COMPLETED).count();
        long overdueAssignments = assignments.stream().filter(a -> a.getStatus() == AssignmentStatus.OVERDUE).count();

        double overallCompletionPercent = totalAssignments == 0 ? 0 : (completedAssignments * 100.0) / totalAssignments;
        double overallCompletionDeltaPercent = computeCompletionDelta(assignments, sevenDaysAgo, fourteenDaysAgo);

        long activeLearners = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.IN_PROGRESS || a.getStatus() == AssignmentStatus.COMPLETED)
                .map(UserAssignment::getUserId)
                .distinct()
                .count();

        long overdueNewThisWeek = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.OVERDUE && a.getDueDate() != null && !a.getDueDate().isBefore(sevenDaysAgo))
                .count();

        Double avgScoreCurrent = assessmentAttemptRepository.getAverageScore();
        double avgAssessmentScorePercent = avgScoreCurrent == null ? 0 : avgScoreCurrent;
        Double thisWeekScore = assessmentAttemptRepository.getAverageScoreBetween(sevenDaysAgo, now);
        Double prevWeekScore = assessmentAttemptRepository.getAverageScoreBetween(fourteenDaysAgo, sevenDaysAgo);
        double avgAssessmentTrendPoints = ((thisWeekScore == null ? 0 : thisWeekScore) - (prevWeekScore == null ? 0 : prevWeekScore)) * 100;

        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long certificatesIssuedThisMonth = issuedCertificateRepository.countByIssuedAtAfter(monthStart);

        List<AdminOverviewResponse.CompletionByDepartment> completionByDepartment = assignmentRepository
                .fetchDepartmentStats(tenant.getId(), AssignmentStatus.COMPLETED)
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

        List<String> riskLabels = completionByDepartment.stream().map(AdminOverviewResponse.CompletionByDepartment::department).toList();
        List<Double> currentScores = completionByDepartment.stream().map(AdminOverviewResponse.CompletionByDepartment::progressPercent).toList();
        List<Double> targetScores = completionByDepartment.stream().map(ignored -> 85.0).toList();
        double currentAverage = currentScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double targetAverage = targetScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        AdminOverviewResponse.RiskMaturity riskMaturity = new AdminOverviewResponse.RiskMaturity(
                riskLabels, currentScores, targetScores, currentAverage, targetAverage
        );

        List<AdminOverviewResponse.OverdueUser> overdueUsers = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.OVERDUE && a.getDueDate() != null)
                .sorted(Comparator.comparing(UserAssignment::getDueDate))
                .limit(10)
                .map(a -> {
                    User user = usersById.get(a.getUserId());
                    TenantUser tenantUser = membershipByUserId.get(a.getUserId());
                    String name = user == null ? "Unknown User" : (user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
                    String department = tenantUser == null || tenantUser.getDepartment() == null ? "UNKNOWN" : tenantUser.getDepartment().name();
                    long daysOverdue = Math.max(0, (now.getEpochSecond() - a.getDueDate().getEpochSecond()) / 86_400);
                    return new AdminOverviewResponse.OverdueUser(name, department, daysOverdue);
                })
                .toList();

        List<AdminOverviewResponse.ActivityItem> activityFeed = buildActivityFeed(tenant, now, usersById);

        List<AdminOverviewResponse.QuizPerformanceByDepartment> quizPerformanceByDepartment = assessmentAttemptRepository
                .getAssessmentPerformanceByDepartment(tenant.getId())
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
                        row[1] == null ? 0 : (Double) row[1],   // already 0-100
                        row[2] == null ? 0 : ((Double) row[2]) * 100 // passRate 0-1 → 0-100
                    );
                })
                .sorted(Comparator.comparing(AdminOverviewResponse.QuizPerformanceByDepartment::department, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<AdminOverviewResponse.HighestFailureLesson> highestFailureLessons = assessmentAttemptRepository
                .getAssessmentFailureRateByDepartment(tenant.getId())
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
                        (String) row[0],   // assessmentConfigId (used as label)
                        dept,              // department
                        row[2] == null ? 0 : ((Double) row[2])
                    );
                })
                .toList();

        AdminOverviewResponse.Kpis kpis = new AdminOverviewResponse.Kpis(
                overallCompletionPercent,
                overallCompletionDeltaPercent,
                new AdminOverviewResponse.ActiveLearners(activeLearners, memberships.size()),
                new AdminOverviewResponse.OverdueSummary(overdueAssignments, overdueNewThisWeek),
                avgAssessmentScorePercent,
                avgAssessmentTrendPoints,
                certificatesIssuedThisMonth
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
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            return null;
        }
        return UUID.fromString(authentication.getDetails().toString());
    }

    private Tenant currentTenant() {
        String slug = TenantContext.getTenant();
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));
    }

    private long countRiskUsers() {
        Tenant tenant = currentTenant();
        List<UUID> learnerUserIds = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .map(TenantUser::getUserId)
                .toList();

        List<UserAssignment> assignments = assignmentRepository.findAll().stream()
                .filter(a -> learnerUserIds.contains(a.getUserId()))
                .toList();

        Map<UUID, List<UserAssignment>> userAssignments =
                assignments.stream().collect(Collectors.groupingBy(UserAssignment::getUserId));

        long riskUsersCount = 0;

        for (var entry : userAssignments.entrySet()) {
            UUID userId = entry.getKey();
            List<UserAssignment> userAssgn = entry.getValue();

            long completed = progressRepository.countCompletedByUser(userId);
            long total = userAssgn.size();

            double completionPercent = total == 0 ? 0 : (completed * 100.0) / total;
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

    private double computeCompletionDelta(List<UserAssignment> assignments, Instant sevenDaysAgo, Instant fourteenDaysAgo) {
        List<UserAssignment> currentWindow = assignments.stream()
                .filter(a -> a.getAssignedAt() != null && !a.getAssignedAt().isBefore(sevenDaysAgo))
                .toList();
        List<UserAssignment> previousWindow = assignments.stream()
                .filter(a -> a.getAssignedAt() != null
                        && !a.getAssignedAt().isBefore(fourteenDaysAgo)
                        && a.getAssignedAt().isBefore(sevenDaysAgo))
                .toList();

        double currentRate = currentWindow.isEmpty() ? 0 : (currentWindow.stream().filter(a -> a.getStatus() == AssignmentStatus.COMPLETED).count() * 100.0) / currentWindow.size();
        double previousRate = previousWindow.isEmpty() ? 0 : (previousWindow.stream().filter(a -> a.getStatus() == AssignmentStatus.COMPLETED).count() * 100.0) / previousWindow.size();
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

    private List<AdminOverviewResponse.ActivityItem> buildActivityFeed(
            Tenant tenant,
            Instant now,
            Map<UUID, User> usersById
    ) {
        return auditLogRepository.findByTenantSlugAndActionIn(
                tenant.getSlug(), 
                MAJOR_ACTIVITY_ACTIONS,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
            ).stream()
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

                switch (log.getAction()) {
                    case CERTIFICATE_ISSUED -> {
                        actionText = userName + " earned a new certificate";
                        type = AdminOverviewResponse.ActivityItem.Type.SUCCESS;
                    }
                    case QUIZ_PASSED -> {
                        actionText = userName + " passed an assessment";
                        type = AdminOverviewResponse.ActivityItem.Type.SUCCESS;
                    }
                    case COURSE_COMPLETED -> {
                        actionText = userName + " completed a track";
                        type = AdminOverviewResponse.ActivityItem.Type.SUCCESS;
                    }
                    case QUIZ_ATTEMPT -> {
                        actionText = userName + " attempted an assessment";
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    case LESSON_COMPLETED -> {
                        actionText = userName + " completed a lesson";
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    case ASSIGNMENT_OVERDUE -> {
                        actionText = userName + " has an overdue assignment";
                        type = AdminOverviewResponse.ActivityItem.Type.WARNING;
                    }
                    case QUIZ_FAILED_RETRY_AVAILABLE -> {
                        actionText = userName + " failed an assessment but can retry";
                        type = AdminOverviewResponse.ActivityItem.Type.WARNING;
                    }
                    case ASSIGNMENT_ESCALATION_SENT -> {
                        actionText = "Escalation email sent for " + userName;
                        type = AdminOverviewResponse.ActivityItem.Type.WARNING;
                    }
                    case QUIZ_LOCKED_MAX_ATTEMPTS -> {
                        actionText = userName + " was locked out of an assessment (max attempts)";
                        type = AdminOverviewResponse.ActivityItem.Type.ERROR;
                    }
                    case COURSE_FAILED -> {
                        actionText = userName + " failed a track";
                        type = AdminOverviewResponse.ActivityItem.Type.ERROR;
                    }
                    case CREATE_USER -> {
                        actionText = "New user account created";
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    case ASSIGN_TRACK -> {
                        actionText = "Track assigned to " + userName;
                        type = AdminOverviewResponse.ActivityItem.Type.INFO;
                    }
                    default -> actionText = userName + " triggered " + log.getAction().name().replace("_", " ").toLowerCase();
                }

                // If we have details (JSON/String), we could potentially extract the track title, but for now we keep it generic and safe
                // You could parse `log.getDetails()` if it contains {"trackTitle": "..."} to say "completed track 'Security'"

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
        Instant now = Instant.now();

        // Only LEARNER roles
        List<UUID> learnerUserIds = tenantUserRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(m -> "LEARNER".equals(m.getRole()))
                .map(TenantUser::getUserId)
                .toList();

        // Filter down to requested users if caller supplied a list
        List<UUID> scopedUserIds = (targetUserIds == null || targetUserIds.isEmpty())
                ? learnerUserIds
                : learnerUserIds.stream().filter(targetUserIds::contains).toList();

        // Find overdue assignments (past due date, not completed) for scoped users
        List<UserAssignment> overdueAssignments = assignmentRepository.findAll().stream()
                .filter(a -> scopedUserIds.contains(a.getUserId()))
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
}

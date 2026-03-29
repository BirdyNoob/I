package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.*;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.QuizAttemptRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final TenantSchemaService tenantSchemaService;

    public AdminAnalyticsService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            UserAssignmentRepository assignmentRepository,
            QuizAttemptRepository quizAttemptRepository,
            LessonProgressRepository progressRepository,
            LessonRepository lessonRepository,
            TenantSchemaService tenantSchemaService
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.assignmentRepository = assignmentRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getOverview() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        long totalUsers = tenantUserRepository.findByTenantId(tenant.getId()).size();

        long totalAssignments = assignmentRepository.count();

        long completedAssignments =
                assignmentRepository.countByStatus(AssignmentStatus.COMPLETED);

        double completionRate =
                totalAssignments == 0 ? 0 :
                        (completedAssignments * 100.0) / totalAssignments;

        Double avgScore = quizAttemptRepository.getAverageScore();

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

        List<UserAssignment> assignments = assignmentRepository.findAll();

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
                    quizAttemptRepository.getAverageScoreByUser(userId);

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

        List<Object[]> stats = quizAttemptRepository.getLessonStats();

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
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId());

        // Group memberships by department
        Map<String, List<TenantUser>> byDept =
                memberships.stream()
                        .collect(Collectors.groupingBy(m ->
                                m.getDepartment() == null ? "UNKNOWN" : m.getDepartment()
                        ));

        List<DepartmentPerformanceResponse> result = new ArrayList<>();

        for (var entry : byDept.entrySet()) {

            String department = entry.getKey();
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
                        quizAttemptRepository.getAverageScoreByUser(userId);

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
    public AdminDashboardResponse getDashboard() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Tenant tenant = currentTenant();
        long totalUsers = tenantUserRepository.findByTenantId(tenant.getId()).size();

        long totalAssignments = assignmentRepository.count();

        long completed = assignmentRepository.countByStatus(AssignmentStatus.COMPLETED);

        long overdue = assignmentRepository.countByStatus(AssignmentStatus.OVERDUE);

        long failed = assignmentRepository.countByStatus(AssignmentStatus.FAILED);

        long riskUsersCount = countRiskUsers();

        double completionRate = totalAssignments == 0 ? 0 :
                (completed * 100.0) / totalAssignments;

        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minusSeconds(7L * 24 * 60 * 60);
        Instant sevenDaysFromNow = now.plusSeconds(7L * 24 * 60 * 60);

        DashboardTimeInsights timeInsights = new DashboardTimeInsights(
                assignmentRepository.countByAssignedAtAfter(sevenDaysAgo),
                quizAttemptRepository.countByAttemptedAtAfter(sevenDaysAgo),
                assignmentRepository.countByDueDateBetweenAndStatusIn(
                        now,
                        sevenDaysFromNow,
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS)
                )
        );

        List<DepartmentStat> deptStats = rankDepartmentStats(
                assignmentRepository.fetchDepartmentStats(tenant.getId(), AssignmentStatus.COMPLETED)
                        .stream()
                        .map(r -> new DepartmentAggregate(
                                (String) r[0],
                                ((Number) r[1]).longValue(),
                                ((Number) r[2]).longValue()
                        ))
                        .toList()
        );

        return new AdminDashboardResponse(
                totalUsers,
                totalAssignments,
                completed,
                overdue,
                failed,
                riskUsersCount,
                completionRate,
                timeInsights,
                deptStats
        );
    }

    private Tenant currentTenant() {
        String slug = TenantContext.getTenant();
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));
    }

    private long countRiskUsers() {
        List<UserAssignment> assignments = assignmentRepository.findAll();

        Map<UUID, List<UserAssignment>> userAssignments =
                assignments.stream().collect(Collectors.groupingBy(UserAssignment::getUserId));

        long riskUsersCount = 0;

        for (var entry : userAssignments.entrySet()) {
            UUID userId = entry.getKey();
            List<UserAssignment> userAssgn = entry.getValue();

            long completed = progressRepository.countCompletedByUser(userId);
            long total = userAssgn.size();

            double completionPercent = total == 0 ? 0 : (completed * 100.0) / total;
            Double avgScore = quizAttemptRepository.getAverageScoreByUser(userId);
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

    private List<DepartmentStat> rankDepartmentStats(List<DepartmentAggregate> aggregates) {
        List<DepartmentAggregate> sorted = aggregates.stream()
                .sorted(
                        Comparator.comparingDouble(DepartmentAggregate::completionRate).reversed()
                                .thenComparing(DepartmentAggregate::completed, Comparator.reverseOrder())
                                .thenComparing(DepartmentAggregate::total, Comparator.reverseOrder())
                                .thenComparing(
                                        aggregate -> aggregate.department() == null ? "UNKNOWN" : aggregate.department(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .toList();

        List<DepartmentStat> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            DepartmentAggregate aggregate = sorted.get(i);
            ranked.add(new DepartmentStat(
                    i + 1,
                    aggregate.department() == null ? "UNKNOWN" : aggregate.department(),
                    aggregate.total(),
                    aggregate.completed(),
                    aggregate.completionRate()
            ));
        }

        return ranked;
    }

    private record DepartmentAggregate(
            String department,
            long total,
            long completed
    ) {
        double completionRate() {
            return total == 0 ? 0 : (completed * 100.0) / total;
        }
    }
}

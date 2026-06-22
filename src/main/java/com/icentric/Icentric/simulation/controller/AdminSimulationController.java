package com.icentric.Icentric.simulation.controller;

import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.identity.service.TenantAccessGuard;
import com.icentric.Icentric.common.security.AdminScopeHelper;
import com.icentric.Icentric.simulation.dto.SimulationAnalyticsResponse;
import com.icentric.Icentric.simulation.dto.SimulationDashboardResponse;
import com.icentric.Icentric.simulation.entity.Simulation;
import com.icentric.Icentric.simulation.entity.SimulationAttempt;
import com.icentric.Icentric.simulation.repository.SimulationAttemptRepository;
import com.icentric.Icentric.simulation.repository.SimulationRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/simulations")
public class AdminSimulationController {

    private final SimulationAttemptRepository attemptRepository;
    private final SimulationRepository simulationRepository;
    private final UserRepository userRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final TenantUserRepository tenantUserRepository;
    private final AdminScopeHelper adminScopeHelper;

    public AdminSimulationController(SimulationAttemptRepository attemptRepository,
                                     SimulationRepository simulationRepository,
                                     UserRepository userRepository,
                                     TenantAccessGuard tenantAccessGuard,
                                     TenantUserRepository tenantUserRepository,
                                     AdminScopeHelper adminScopeHelper) {
        this.attemptRepository = attemptRepository;
        this.simulationRepository = simulationRepository;
        this.userRepository = userRepository;
        this.tenantAccessGuard = tenantAccessGuard;
        this.tenantUserRepository = tenantUserRepository;
        this.adminScopeHelper = adminScopeHelper;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SimulationDashboardResponse> getDashboard() {
        String tenantSlug = TenantContext.getTenant();
        UUID tenantId = tenantAccessGuard.currentTenantId();

        List<SimulationAttempt> allAttempts = attemptRepository.findByTenantSlug(tenantSlug);
        List<SimulationAttempt> failures = attemptRepository.findByTenantSlugAndPassedFalse(tenantSlug);

        // Apply admin scoping: standard admins only see their onboarded users
        AdminScopeHelper.AdminScope scope = adminScopeHelper.resolveForCurrentUser();
        if (scope.isStandardAdmin()) {
            allAttempts = allAttempts.stream().filter(a -> scope.isInScope(a.getUserId())).toList();
            failures = failures.stream().filter(a -> scope.isInScope(a.getUserId())).toList();
        }

        long totalUsers = tenantUserRepository.countByTenantId(tenantId);

        // Collect user info
        Set<UUID> userIds = allAttempts.stream().map(SimulationAttempt::getUserId).collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepository.findByIdIn(new ArrayList<>(userIds))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, TenantUser> memberships = tenantUserRepository.findByTenantIdAndUserIdIn(tenantId, userIds)
                .stream().collect(Collectors.toMap(TenantUser::getUserId, tu -> tu, (a, b) -> a));

        // Sim title lookup
        Map<String, String> simTitles = simulationRepository.findAll().stream()
                .collect(Collectors.toMap(Simulation::getSimId, Simulation::getTitle, (a, b) -> a));

        // KPIs
        long totalAttempts = allAttempts.size();
        long totalPassed = allAttempts.stream().filter(SimulationAttempt::isPassed).count();
        long totalFailed = totalAttempts - totalPassed;
        double avgScore = allAttempts.stream().mapToInt(SimulationAttempt::getPercentage).average().orElse(0);
        double passRate = totalAttempts > 0 ? round(100.0 * totalPassed / totalAttempts) : 0;
        long usersAttempted = userIds.size();
        double completionRate = totalUsers > 0 ? round(100.0 * usersAttempted / totalUsers) : 0;

        SimulationDashboardResponse.Kpis kpis = new SimulationDashboardResponse.Kpis();
        kpis.setTotalAttempts(totalAttempts);
        kpis.setTotalPassed(totalPassed);
        kpis.setTotalFailed(totalFailed);
        kpis.setPassRate(passRate);
        kpis.setAvgScore(round(avgScore));
        kpis.setRiskUserCount(failures.stream().map(SimulationAttempt::getUserId).distinct().count());
        kpis.setCompletionRate(completionRate);

        // Risk users — one entry per user, with list of failed simulations
        Map<UUID, List<SimulationAttempt>> failuresByUser = failures.stream()
                .collect(Collectors.groupingBy(SimulationAttempt::getUserId));

        List<SimulationDashboardResponse.RiskUser> riskUsers = failuresByUser.entrySet().stream()
                .map(e -> {
                    UUID uid = e.getKey();
                    List<SimulationAttempt> userFailures = e.getValue();
                    int lowestScore = userFailures.stream().mapToInt(SimulationAttempt::getPercentage).min().orElse(0);
                    SimulationAttempt latest = userFailures.stream()
                            .max(Comparator.comparing(SimulationAttempt::getCompletedAt)).orElse(userFailures.get(0));

                    SimulationDashboardResponse.RiskUser ru = new SimulationDashboardResponse.RiskUser();
                    ru.setUserId(uid.toString());
                    User u = usersById.get(uid);
                    ru.setName(u != null ? u.getName() : "Unknown");
                    ru.setEmail(u != null ? u.getEmail() : "");
                    TenantUser tu = memberships.get(uid);
                    ru.setDepartment(tu != null && tu.getDepartment() != null ? tu.getDepartment().getDisplayName() : "Unassigned");
                    ru.setScore(lowestScore);
                    ru.setFailedSimulations(userFailures.stream()
                            .map(a -> simTitles.getOrDefault(a.getSimId(), a.getSimId()))
                            .toList());
                    ru.setCompletedAt(latest.getCompletedAt());
                    ru.setRiskLevel(lowestScore <= 33 ? "HIGH" : "MEDIUM");
                    return ru;
                })
                .sorted(Comparator.comparingInt(SimulationDashboardResponse.RiskUser::getScore))
                .limit(20)
                .toList();

        // Department breakdown
        Map<String, List<SimulationAttempt>> byDept = new HashMap<>();
        for (SimulationAttempt a : allAttempts) {
            TenantUser tu = memberships.get(a.getUserId());
            String dept = (tu != null && tu.getDepartment() != null) ? tu.getDepartment().getDisplayName() : "Unassigned";
            byDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(a);
        }

        List<SimulationDashboardResponse.DepartmentStat> deptStats = byDept.entrySet().stream()
                .map(e -> {
                    List<SimulationAttempt> deptAttempts = e.getValue();
                    long deptUsers = deptAttempts.stream().map(SimulationAttempt::getUserId).distinct().count();
                    double deptAvg = deptAttempts.stream().mapToInt(SimulationAttempt::getPercentage).average().orElse(0);
                    long deptPassed = deptAttempts.stream().filter(SimulationAttempt::isPassed).count();
                    double deptPassRate = deptAttempts.size() > 0 ? round(100.0 * deptPassed / deptAttempts.size()) : 0;

                    SimulationDashboardResponse.DepartmentStat ds = new SimulationDashboardResponse.DepartmentStat();
                    ds.setDepartment(e.getKey());
                    ds.setTotalUsers(deptUsers);
                    ds.setAttempted(deptAttempts.size());
                    ds.setAvgScore(round(deptAvg));
                    ds.setPassRate(deptPassRate);
                    ds.setStatus(deptPassRate >= 70 ? "ON_TRACK" : deptPassRate >= 40 ? "AT_RISK" : "CRITICAL");
                    return ds;
                })
                .sorted(Comparator.comparingDouble(SimulationDashboardResponse.DepartmentStat::getPassRate))
                .toList();

        // Per-simulation performance
        Map<String, List<SimulationAttempt>> bySim = allAttempts.stream()
                .collect(Collectors.groupingBy(SimulationAttempt::getSimId));

        List<SimulationDashboardResponse.SimPerformance> simPerf = bySim.entrySet().stream()
                .map(e -> {
                    List<SimulationAttempt> simAttempts = e.getValue();
                    long passed = simAttempts.stream().filter(SimulationAttempt::isPassed).count();
                    double avg = simAttempts.stream().mapToInt(SimulationAttempt::getPercentage).average().orElse(0);

                    SimulationDashboardResponse.SimPerformance sp = new SimulationDashboardResponse.SimPerformance();
                    sp.setSimId(e.getKey());
                    sp.setTitle(simTitles.getOrDefault(e.getKey(), e.getKey()));
                    sp.setAttempts(simAttempts.size());
                    sp.setPassRate(round(100.0 * passed / simAttempts.size()));
                    sp.setAvgScore(round(avg));
                    sp.setFailCount(simAttempts.size() - passed);
                    return sp;
                }).toList();

        // Recent failures (last 10)
        List<SimulationDashboardResponse.RecentFailure> recentFailures = failures.stream()
                .sorted(Comparator.comparing(SimulationAttempt::getCompletedAt).reversed())
                .limit(10)
                .map(a -> {
                    SimulationDashboardResponse.RecentFailure rf = new SimulationDashboardResponse.RecentFailure();
                    rf.setUserId(a.getUserId().toString());
                    User u = usersById.get(a.getUserId());
                    rf.setName(u != null ? u.getName() : "Unknown");
                    rf.setEmail(u != null ? u.getEmail() : "");
                    TenantUser tu = memberships.get(a.getUserId());
                    rf.setDepartment(tu != null && tu.getDepartment() != null ? tu.getDepartment().getDisplayName() : "Unassigned");
                    rf.setSimTitle(simTitles.getOrDefault(a.getSimId(), a.getSimId()));
                    rf.setScore(a.getPercentage());
                    rf.setCompletedAt(a.getCompletedAt());
                    return rf;
                }).toList();

        // Build response
        SimulationDashboardResponse response = new SimulationDashboardResponse();
        response.setKpis(kpis);
        response.setRiskUsers(riskUsers);
        response.setDepartmentBreakdown(deptStats);
        response.setSimulationPerformance(simPerf);
        response.setRecentFailures(recentFailures);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics")
    public ResponseEntity<SimulationAnalyticsResponse> getAnalytics() {
        String tenantSlug = TenantContext.getTenant();

        long totalAttempts = attemptRepository.countByTenantSlug(tenantSlug);
        long totalPassed = attemptRepository.countByTenantSlugAndPassedTrue(tenantSlug);
        Double avgPct = attemptRepository.avgPercentageByTenantSlug(tenantSlug);

        SimulationAnalyticsResponse response = new SimulationAnalyticsResponse();
        response.setTotalAttempts(totalAttempts);
        response.setTotalPassed(totalPassed);
        response.setPassRate(totalAttempts > 0 ? Math.round(100.0 * totalPassed / totalAttempts * 10) / 10.0 : 0);
        response.setAvgScorePercentage(avgPct != null ? Math.round(avgPct * 10) / 10.0 : 0);

        List<SimulationAnalyticsResponse.SimulationStat> stats = new ArrayList<>();
        for (Simulation sim : simulationRepository.findAll()) {
            String simId = sim.getSimId();
            long attempts = attemptRepository.countBySimId(simId);
            if (attempts == 0) continue;

            long passed = attemptRepository.countBySimIdAndPassedTrue(simId);
            Double avg = attemptRepository.avgPercentageBySimId(simId);

            SimulationAnalyticsResponse.SimulationStat stat = new SimulationAnalyticsResponse.SimulationStat();
            stat.setSimId(simId);
            stat.setTitle(sim.getTitle());
            stat.setAttempts(attempts);
            stat.setPassed(passed);
            stat.setPassRate(Math.round(100.0 * passed / attempts * 10) / 10.0);
            stat.setAvgPercentage(avg != null ? Math.round(avg * 10) / 10.0 : 0);
            stats.add(stat);
        }

        response.setSimulations(stats);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attempts")
    public ResponseEntity<List<SimulationAttempt>> getAttempts(
            @RequestParam(required = false) UUID userId) {
        String tenantSlug = TenantContext.getTenant();
        List<SimulationAttempt> attempts;
        if (userId != null) {
            attempts = attemptRepository.findByUserIdOrderByCompletedAtDesc(userId);
        } else {
            attempts = attemptRepository.findByTenantSlugOrderByCompletedAtDesc(tenantSlug);
        }

        // Apply admin scoping
        AdminScopeHelper.AdminScope scope = adminScopeHelper.resolveForCurrentUser();
        if (scope.isStandardAdmin()) {
            attempts = attempts.stream().filter(a -> scope.isInScope(a.getUserId())).toList();
        }

        return ResponseEntity.ok(attempts);
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

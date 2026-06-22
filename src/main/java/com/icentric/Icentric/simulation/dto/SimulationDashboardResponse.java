package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
public class SimulationDashboardResponse {

    private Kpis kpis;
    @JsonProperty("risk_users")
    private List<RiskUser> riskUsers;
    @JsonProperty("department_breakdown")
    private List<DepartmentStat> departmentBreakdown;
    @JsonProperty("simulation_performance")
    private List<SimPerformance> simulationPerformance;
    @JsonProperty("recent_failures")
    private List<RecentFailure> recentFailures;

    @Data
    public static class Kpis {
        @JsonProperty("total_attempts")
        private long totalAttempts;
        @JsonProperty("total_passed")
        private long totalPassed;
        @JsonProperty("total_failed")
        private long totalFailed;
        @JsonProperty("pass_rate")
        private double passRate;
        @JsonProperty("avg_score")
        private double avgScore;
        @JsonProperty("risk_user_count")
        private long riskUserCount;
        @JsonProperty("completion_rate")
        private double completionRate;
    }

    @Data
    public static class RiskUser {
        @JsonProperty("user_id")
        private String userId;
        private String name;
        private String email;
        private String department;
        private int score;
        @JsonProperty("failed_simulations")
        private List<String> failedSimulations;
        @JsonProperty("completed_at")
        private Instant completedAt;
        @JsonProperty("risk_level")
        private String riskLevel; // HIGH, MEDIUM
    }

    @Data
    public static class DepartmentStat {
        private String department;
        @JsonProperty("total_users")
        private long totalUsers;
        private long attempted;
        @JsonProperty("avg_score")
        private double avgScore;
        @JsonProperty("pass_rate")
        private double passRate;
        private String status; // ON_TRACK, AT_RISK, CRITICAL
    }

    @Data
    public static class SimPerformance {
        @JsonProperty("sim_id")
        private String simId;
        private String title;
        private long attempts;
        @JsonProperty("pass_rate")
        private double passRate;
        @JsonProperty("avg_score")
        private double avgScore;
        @JsonProperty("fail_count")
        private long failCount;
    }

    @Data
    public static class RecentFailure {
        @JsonProperty("user_id")
        private String userId;
        private String name;
        private String email;
        private String department;
        @JsonProperty("sim_title")
        private String simTitle;
        private int score;
        @JsonProperty("completed_at")
        private Instant completedAt;
    }
}

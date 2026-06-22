package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class SimulationAnalyticsResponse {
    @JsonProperty("total_attempts")
    private long totalAttempts;
    @JsonProperty("total_passed")
    private long totalPassed;
    @JsonProperty("pass_rate")
    private double passRate;
    @JsonProperty("avg_score_percentage")
    private double avgScorePercentage;
    private List<SimulationStat> simulations;

    @Data
    public static class SimulationStat {
        @JsonProperty("sim_id")
        private String simId;
        private String title;
        private long attempts;
        private long passed;
        @JsonProperty("pass_rate")
        private double passRate;
        @JsonProperty("avg_percentage")
        private double avgPercentage;
    }
}

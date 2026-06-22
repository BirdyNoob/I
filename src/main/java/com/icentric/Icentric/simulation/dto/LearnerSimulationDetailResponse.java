package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * Learner-safe version of SimulationDetailResponse.
 * Strips correct_answer and feedback from scenes.
 */
@Data
public class LearnerSimulationDetailResponse {
    private String id;
    private String title;
    @JsonProperty("accent_color")
    private String accentColor;
    private SimulationDetailResponse.Badge badge;
    @JsonProperty("total_decisions")
    private int totalDecisions;
    private SimulationDetailResponse.Intro intro;
    private List<LearnerScene> scenes;
    private LearnerResults results;

    @Data
    public static class LearnerScene {
        private String id;
        private int order;
        @JsonProperty("context_bar")
        private SimulationDetailResponse.ContextBar contextBar;
        private Object mockup;
        private LearnerQuestion question;
        @JsonProperty("next_button_text")
        private String nextButtonText;
        // NO feedback field
    }

    @Data
    public static class LearnerQuestion {
        @JsonProperty("number_label")
        private String numberLabel;
        private String text;
        private List<SimulationDetailResponse.Option> options;
        // NO correct_answer field
    }

    @Data
    public static class LearnerResults {
        @JsonProperty("score_ring_circumference")
        private double scoreRingCircumference;
        private List<SimulationDetailResponse.BreakdownItem> breakdown;
        // NO titles/subtitles — resolved server-side on submit
    }
}

package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Full simulation with user's answers and feedback included — for review mode.
 * Same flow as playing, but with answers revealed.
 */
@Data
public class SimulationReviewResponse {
    private String id;
    private String title;
    @JsonProperty("accent_color")
    private String accentColor;
    private SimulationDetailResponse.Badge badge;
    @JsonProperty("total_decisions")
    private int totalDecisions;
    private SimulationDetailResponse.Intro intro;
    private List<ReviewScene> scenes;
    private ReviewResults results;

    @Data
    public static class ReviewScene {
        private String id;
        private int order;
        @JsonProperty("context_bar")
        private SimulationDetailResponse.ContextBar contextBar;
        private Object mockup;
        private ReviewQuestion question;
        private SimulationDetailResponse.Feedback feedback;
        @JsonProperty("next_button_text")
        private String nextButtonText;
    }

    @Data
    public static class ReviewQuestion {
        @JsonProperty("number_label")
        private String numberLabel;
        private String text;
        @JsonProperty("correct_answer")
        private String correctAnswer;
        @JsonProperty("user_answer")
        private String userAnswer;
        @JsonProperty("is_correct")
        private boolean correct;
        private List<SimulationDetailResponse.Option> options;
    }

    @Data
    public static class ReviewResults {
        @JsonProperty("score_ring_circumference")
        private double scoreRingCircumference;
        @JsonProperty("total_correct")
        private int totalCorrect;
        @JsonProperty("total_questions")
        private int totalQuestions;
        private int percentage;
        private boolean passed;
        private String title;
        private String subtitle;
        private List<SimulationDetailResponse.BreakdownItem> breakdown;
    }
}

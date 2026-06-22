package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class SimulationResultResponse {
    @JsonProperty("total_correct")
    private int totalCorrect;
    @JsonProperty("total_questions")
    private int totalQuestions;
    private int percentage;
    private boolean passed;
    private String title;
    private String subtitle;
    private List<BreakdownEntry> breakdown;

    @Data
    public static class BreakdownEntry {
        private String label;
        @JsonProperty("scene_id")
        private String sceneId;
        @JsonProperty("user_answer")
        private String userAnswer;
        @JsonProperty("correct_answer")
        private String correctAnswer;
        @JsonProperty("is_correct")
        private boolean correct;
        @JsonProperty("feedback_title")
        private String feedbackTitle;
        @JsonProperty("feedback_text")
        private String feedbackText;
    }
}

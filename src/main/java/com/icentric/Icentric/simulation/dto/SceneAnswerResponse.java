package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SceneAnswerResponse {
    @JsonProperty("is_correct")
    private boolean correct;
    @JsonProperty("correct_answer")
    private String correctAnswer;
    @JsonProperty("feedback_title")
    private String feedbackTitle;
    @JsonProperty("feedback_text")
    private String feedbackText;
}

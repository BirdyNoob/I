package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssessmentMetadataDto {
    private String title;
    private String subtitle;
    
    @JsonProperty("total_questions")
    private Integer totalQuestions;
    
    @JsonProperty("time_limit_minutes")
    private Integer timeLimitMinutes;
    
    @JsonProperty("passing_score_percentage")
    private Integer passingScorePercentage;
    
    @JsonProperty("retake_policy")
    private String retakePolicy;
}

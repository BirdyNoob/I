package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AssessmentResultDto {
    private String status;           // "passed" | "failed"
    private Integer score;
    private Integer passingScore;
    private Integer correctCount;
    private Integer totalQuestions;
    private Integer previousScore;       // null on first attempt
    private Integer improvementPercent;  // null on first attempt
}

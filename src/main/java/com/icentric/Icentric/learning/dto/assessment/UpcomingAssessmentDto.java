package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpcomingAssessmentDto {
    private String assessmentId;
    private String title;
    private Integer attemptNumber;
    private Integer lastScore;
    private Integer passingScore;
    private String retakeAvailableAt; // ISO-8601, null if immediately available
}

package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssessmentStatsDto {
    private Integer averageScore;
    private Integer finalAssessmentsPassed;
    private Integer finalAssessmentsTotal;
    private Integer bestScore;
}

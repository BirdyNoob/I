package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssessmentDashboardResponse {
    private String learnerId;
    private AssessmentStatsDto summary;
    private UpcomingAssessmentDto upcoming;
    private List<FinalAssessmentDto> finalAssessments;
}

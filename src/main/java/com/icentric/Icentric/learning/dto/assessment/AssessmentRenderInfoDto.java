package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssessmentRenderInfoDto {
    private String id;
    private String title;
    private String trackName;
    private Integer totalQuestions;
    private Integer timeLimitMinutes;
    private Integer passingScorePercent;
    private String retakesAllowed;
    private Integer attemptNumber;
    private List<String> guidelines;
}

package com.icentric.Icentric.learning.dto.assessment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentReviewResponse {
    private String assessmentId;
    private String title;
    private String trackName;
    private Integer score;
    private Integer passingScore;
    private String status;
    private String completedAt;
    private List<AssessmentQuestionReviewDto> questions;
}

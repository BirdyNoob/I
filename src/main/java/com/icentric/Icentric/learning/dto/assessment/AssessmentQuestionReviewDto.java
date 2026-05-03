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
public class AssessmentQuestionReviewDto {
    private String questionId;
    private Integer orderIndex;
    private String text;
    private String type;
    private String topic;
    private String scenarioContext;
    private String selectedOptionId;
    private String correctOptionId;
    private boolean isCorrect;
    private String explanation;
    private List<AssessmentRenderOptionDto> options;
}

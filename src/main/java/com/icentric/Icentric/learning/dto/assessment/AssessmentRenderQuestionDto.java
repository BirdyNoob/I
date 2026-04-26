package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AssessmentRenderQuestionDto {
    private String questionId;
    private Integer orderIndex;
    private String type;   // MULTIPLE_CHOICE | SCENARIO | TRUE_FALSE
    private String topic;
    private Integer difficulty; // nullable allowed
    private String scenarioContext; // only for type=scenario
    private String text;
    private List<AssessmentRenderOptionDto> options;
}

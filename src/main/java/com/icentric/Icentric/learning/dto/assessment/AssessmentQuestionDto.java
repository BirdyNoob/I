package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssessmentQuestionDto {
    private String id;
    private Integer number;
    private String type;
    private String topic;
    private Integer difficulty;
    
    @JsonProperty("scenario_context")
    private String scenarioContext;
    
    private String text;
    private List<AssessmentOptionDto> options;
}

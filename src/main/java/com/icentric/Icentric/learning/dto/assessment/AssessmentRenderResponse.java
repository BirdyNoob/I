package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssessmentRenderResponse {
    private AssessmentRenderInfoDto assessment;
    private List<AssessmentRenderQuestionDto> questions;
}

package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssessmentDataResponse {
    private AssessmentMetadataDto assessment;
    private List<AssessmentQuestionDto> questions;
}

package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaveAssessmentProgressResponse {
    private String attemptId;
    private String status;
}

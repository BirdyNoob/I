package com.icentric.Icentric.learning.dto.assessment;

import lombok.Data;
import java.util.List;

@Data
public class SaveAssessmentProgressRequest {
    private String attemptId;
    private Integer timeRemainingSeconds;
    private List<AnswerSubmissionDto> answers;
}

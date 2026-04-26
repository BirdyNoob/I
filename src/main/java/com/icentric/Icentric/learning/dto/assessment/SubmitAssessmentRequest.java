package com.icentric.Icentric.learning.dto.assessment;

import lombok.Data;

import java.util.List;

@Data
public class SubmitAssessmentRequest {
    private String attemptId;
    private Integer timeTakenSeconds;
    private List<AnswerSubmissionDto> answers;
}

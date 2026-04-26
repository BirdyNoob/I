package com.icentric.Icentric.learning.dto.assessment;

import lombok.Data;

@Data
public class AnswerSubmissionDto {
    private String questionId;
    private String selectedOptionId;
    private Boolean flagged;
}

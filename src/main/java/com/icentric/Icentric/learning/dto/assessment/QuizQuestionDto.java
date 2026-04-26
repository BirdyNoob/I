package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizQuestionDto {
    private String question;
    private String userAnswer;
    private Boolean isCorrect;
    private String correctAnswer;
    private String reason;
}

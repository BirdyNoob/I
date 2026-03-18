package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

public record CreateQuestionRequest(

        UUID lessonId,
        String questionText,
        String questionType,
        List<AnswerOption> answers

) {

    public record AnswerOption(
            String answerText,
            boolean isCorrect
    ) {}
}

package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(

        UUID questionId,
        String questionText,
        String questionType,
        List<AnswerOption> answers

) {

    public record AnswerOption(
            UUID answerId,
            String answerText
    ) {}
}

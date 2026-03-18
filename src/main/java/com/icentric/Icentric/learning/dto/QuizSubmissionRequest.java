package com.icentric.Icentric.learning.dto;

import java.util.List;
import java.util.UUID;

public record QuizSubmissionRequest(

        UUID lessonId,
        List<AnswerSubmission> answers

) {

    public record AnswerSubmission(
            UUID questionId,
            UUID answerId
    ) {}
}

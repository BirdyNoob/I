package com.icentric.Icentric.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record QuizSubmissionRequest(

        @NotNull
        UUID lessonId,
        @NotEmpty
        List<@Valid AnswerSubmission> answers

) {

    public record AnswerSubmission(
            @NotNull
            UUID questionId,
            @NotNull
            UUID answerId
    ) {}
}

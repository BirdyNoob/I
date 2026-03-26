package com.icentric.Icentric.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateQuestionRequest(

        @NotNull
        UUID lessonId,
        @NotBlank
        @Size(max = 2000)
        String questionText,
        @NotBlank
        @Size(max = 50)
        String questionType,
        @NotEmpty
        List<@Valid AnswerOption> answers

) {

    public record AnswerOption(
            @NotBlank
            @Size(max = 1000)
            String answerText,
            boolean isCorrect
    ) {}
}

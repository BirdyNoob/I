package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.LessonDetailResponse;
import com.icentric.Icentric.content.service.LessonService;
import com.icentric.Icentric.learning.dto.QuizResultResponse;
import com.icentric.Icentric.learning.dto.QuizSubmissionRequest;
import com.icentric.Icentric.learning.service.QuizService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
@Tag(name = "Lessons (Learner)", description = "APIs for learners to interact with lessons and quizzes")
public class LearnerLessonController {

    private final LessonService service;
    private final QuizService quizService;

    public LearnerLessonController(LessonService service, QuizService quizService) {
        this.quizService = quizService;
        this.service = service;
    }

    @Operation(summary = "Get lesson details", description = "Retrieves details about a specific lesson.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved lesson details"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Lesson not found")
    })
    @GetMapping("/{lessonId}")
    public LessonDetailResponse getLesson(
            @Parameter(description = "UUID of the lesson") @PathVariable UUID lessonId
    ) {
        return service.getLesson(lessonId);
    }

    @Operation(summary = "Submit quiz attempt", description = "Submits a learner's answers for a lesson quiz and returns the results.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully submitted quiz attempt"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/{lessonId}/quiz-attempt")
    public QuizResultResponse submitQuiz(
            @Parameter(description = "UUID of the lesson containing the quiz") @PathVariable UUID lessonId,
            @Valid @RequestBody QuizSubmissionRequest request,
            Authentication authentication
    ) {
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return quizService.submitQuiz(userId, request);
    }
}

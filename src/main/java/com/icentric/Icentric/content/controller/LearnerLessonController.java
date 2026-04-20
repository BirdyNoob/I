package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.dto.LessonDetailResponse;
import com.icentric.Icentric.content.service.LessonService;
import com.icentric.Icentric.learning.dto.QuizResultResponse;
import com.icentric.Icentric.learning.dto.QuizSubmissionRequest;
import com.icentric.Icentric.learning.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Learner-facing lesson endpoints:
 * - GET  /api/v1/lessons/{lessonId}             — fetch lesson content
 * - POST /api/v1/lessons/{lessonId}/quiz-attempt — submit quiz answers
 */
@RestController
@RequestMapping("/api/v1/lessons")
@Tag(name = "Lessons (Learner)", description = "APIs for learners to interact with lessons and quizzes")
public class LearnerLessonController {

    private final LessonService service;
    private final QuizService quizService;

    public LearnerLessonController(LessonService service, QuizService quizService) {
        this.service = service;
        this.quizService = quizService;
    }

    // ── GET lesson detail ──────────────────────────────────────────────────────

    @Operation(
            summary = "Get lesson details",
            description = "Returns title, lessonType, contentJson, videoUrl, and resourceUrl for a lesson. " +
                          "Call this to render the appropriate UI component (video player, scenario, dos/don'ts card, or quiz).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lesson detail returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Lesson not found")
    })
    @PreAuthorize("hasRole('LEARNER')")
    @GetMapping("/{lessonId}")
    public LessonDetailResponse getLesson(
            @Parameter(description = "UUID of the lesson") @PathVariable UUID lessonId,
            Authentication authentication
    ) {
        UUID userId = extractUserId(authentication);
        return service.getLesson(lessonId, userId);
    }

    // ── GET lesson step detail ──────────────────────────────────────────────────

    @Operation(
            summary = "Get lesson step detail",
            description = "Returns the detailed content of a specific lesson step."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lesson step returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Lesson or step not found")
    })
    @PreAuthorize("hasRole('LEARNER')")
    @GetMapping("/{lessonId}/steps/{stepId}")
    public com.icentric.Icentric.content.dto.LessonStepResponse getLessonStep(
            @Parameter(description = "UUID of the lesson") @PathVariable UUID lessonId,
            @Parameter(description = "UUID of the step") @PathVariable UUID stepId,
            Authentication authentication
    ) {
        UUID userId = extractUserId(authentication);
        return service.getLessonStep(lessonId, stepId, userId);
    }

    // ── POST quiz attempt ──────────────────────────────────────────────────────

    @Operation(
            summary = "Submit quiz attempt",
            description = "Submits a learner's answers for the QUIZ lesson and returns the score + pass/fail. " +
                          "Requires the preceding VIDEO_CONCEPT, INTERACTIVE_SCENARIO, and DOS_AND_DONTS lessons " +
                          "to be COMPLETED — otherwise the sequential lock will return HTTP 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quiz attempt evaluated"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Lesson locked — complete preceding lessons first")
    })
    @PreAuthorize("hasRole('LEARNER')")
    @PostMapping("/{lessonId}/quiz-attempt")
    public QuizResultResponse submitQuiz(
            @Parameter(description = "UUID of the QUIZ lesson") @PathVariable UUID lessonId,
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

    // ── PATCH update lesson content (platform admin) ──────────────────────────

    @Operation(
            summary = "Update lesson content",
            description = "Updates title, contentJson, videoUrl, or resourceUrl. " +
                          "lessonType and sortOrder are immutable post-creation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lesson updated"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Lesson not found")
    })
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PatchMapping("/{lessonId}")
    public LessonDetailResponse updateLesson(
            @Parameter(description = "UUID of the lesson") @PathVariable UUID lessonId,
            @Valid @RequestBody CreateLessonRequest request
    ) {
        return service.updateLesson(lessonId, request);
    }

    // ── PATCH publish lesson (platform admin) ─────────────────────────────────

    @Operation(summary = "Publish a lesson", description = "Marks a lesson as published so learners can see it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lesson published"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Lesson not found")
    })
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PatchMapping("/{lessonId}/publish")
    public LessonDetailResponse publishLesson(
            @Parameter(description = "UUID of the lesson") @PathVariable UUID lessonId
    ) {
        return service.publishLesson(lessonId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null) return null;
        Object raw = authentication.getDetails();
        return raw == null ? null : UUID.fromString(raw.toString());
    }
}

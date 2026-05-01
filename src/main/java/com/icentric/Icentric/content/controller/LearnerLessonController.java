package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.dto.LessonDetailResponse;
import com.icentric.Icentric.content.service.LessonService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Learner-facing lesson endpoints:
 * - GET  /api/v1/lessons/{lessonId}               — fetch lesson content
 * - GET  /api/v1/lessons/{lessonId}/steps/{stepId} — fetch step detail
 */
@RestController
@RequestMapping("/api/v1/lessons")
@Tag(name = "Lessons (Learner)", description = "APIs for learners to interact with lessons and quizzes")
public class LearnerLessonController {

    private final LessonService service;

    public LearnerLessonController(LessonService service) {
        this.service = service;
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

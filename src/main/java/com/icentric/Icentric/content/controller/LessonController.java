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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Platform-admin endpoint for creating lessons inside a module.
 * Base path: /api/v1/platform/content/modules/{moduleId}/lessons
 */
@RestController
@RequestMapping("/api/v1/platform/content/modules")
@Tag(name = "Lessons (Platform Admin)", description = "APIs for platform admins to manage lessons within modules")
public class LessonController {

    private final LessonService service;

    public LessonController(LessonService service) {
        this.service = service;
    }

    @Operation(
            summary = "Create a lesson",
            description = "Creates one of the four curriculum lesson types inside a module. " +
                          "lessonType must be VIDEO_CONCEPT, INTERACTIVE_SCENARIO, DOS_AND_DONTS, or QUIZ. " +
                          "The sortOrder should follow 0=VIDEO, 1=SCENARIO, 2=DOS_DONTS, 3=QUIZ " +
                          "to work correctly with sequential locking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lesson created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload or duplicate lessonType in module"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden — platform admin role required")
    })
    @PostMapping("/{moduleId}/lessons")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public LessonDetailResponse createLesson(
            @Parameter(description = "UUID of the parent module") @PathVariable UUID moduleId,
            @Valid @RequestBody CreateLessonRequest request
    ) {
        return service.createLesson(moduleId, request);
    }
}

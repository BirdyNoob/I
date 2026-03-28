package com.icentric.Icentric.content.controller;
import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.entity.Lesson;
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

@RestController
@RequestMapping("/api/v1/platform/content/modules")
@Tag(name = "Lessons (Platform)", description = "APIs for platform admins to manage lessons within modules")
public class LessonController {

    private final LessonService service;

    public LessonController(LessonService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new lesson", description = "Creates a new lesson inside a specified course module.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully created the lesson"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @PostMapping("/{moduleId}/lessons")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Lesson createLesson(
            @Parameter(description = "UUID of the module") @PathVariable UUID moduleId,
            @Valid @RequestBody CreateLessonRequest request
    ) {
        return service.createLesson(moduleId, request);
    }
}

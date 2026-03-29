package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateModuleRequest;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.service.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Platform-admin CRUD for course modules.
 * Base: /api/v1/platform/content/tracks/{trackId}/modules
 */
@RestController
@RequestMapping("/api/v1/platform/content/tracks")
@Tag(name = "Modules (Platform Admin)", description = "APIs for platform admins to manage course modules within tracks")
public class ModuleController {

    private final ModuleService service;

    public ModuleController(ModuleService service) {
        this.service = service;
    }

    // ── POST /tracks/{trackId}/modules ────────────────────────────────────────

    @Operation(
            summary = "Create a module",
            description = "Creates a new module inside a track. After creation, add " +
                          "the four curriculum lessons (VIDEO_CONCEPT, INTERACTIVE_SCENARIO, DOS_AND_DONTS, QUIZ) " +
                          "via the lesson endpoint before publishing the track.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Module created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden — platform admin role required")
    })
    @PostMapping("/{trackId}/modules")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CourseModule createModule(
            @Parameter(description = "UUID of the parent track") @PathVariable UUID trackId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return service.createModule(trackId, request);
    }

    // ── PUT /tracks/{trackId}/modules/{moduleId} ──────────────────────────────

    @Operation(
            summary = "Update a module",
            description = "Updates the module title or sortOrder.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Module updated"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Module not found")
    })
    @PutMapping("/{trackId}/modules/{moduleId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CourseModule updateModule(
            @Parameter(description = "UUID of the track (for URL consistency)") @PathVariable UUID trackId,
            @Parameter(description = "UUID of the module") @PathVariable UUID moduleId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return service.updateModule(moduleId, request);
    }

    // ── DELETE /tracks/{trackId}/modules/{moduleId} ───────────────────────────

    @Operation(
            summary = "Delete a module",
            description = "Deletes the module and all its lessons. " +
                          "Only safe while the parent track is in DRAFT status.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Module deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Module not found")
    })
    @DeleteMapping("/{trackId}/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void deleteModule(
            @Parameter(description = "UUID of the track") @PathVariable UUID trackId,
            @Parameter(description = "UUID of the module") @PathVariable UUID moduleId
    ) {
        service.deleteModule(moduleId);
    }
}

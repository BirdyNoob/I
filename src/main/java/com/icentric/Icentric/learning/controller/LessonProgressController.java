package com.icentric.Icentric.learning.controller;
import com.icentric.Icentric.learning.dto.LessonProgressRequest;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.service.LessonProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
@Tag(name = "Lesson Progress (Learner)", description = "APIs for learners to update their lesson progress")
public class LessonProgressController {

    private final LessonProgressService service;

    public LessonProgressController(
            LessonProgressService service
    ) {
        this.service = service;
    }

    @Operation(summary = "Update Lesson Progress", description = "Updates the progress of a specific lesson for the currently authenticated learner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully updated lesson progress"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - learner role required")
    })
    @PostMapping("/progress")
    @PreAuthorize("hasRole('LEARNER')")
    public LessonProgress updateProgress(
            @Valid @RequestBody LessonProgressRequest request
    ) {
        Authentication authentication = org.springframework.security.core.context
                .SecurityContextHolder
                .getContext()
                .getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.updateProgress(userId, request);
    }
}

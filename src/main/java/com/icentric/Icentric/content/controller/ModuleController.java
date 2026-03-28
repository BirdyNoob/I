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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/content/tracks")
@Tag(name = "Modules (Platform)", description = "APIs for platform admins to manage course modules within tracks")
public class ModuleController {

    private final ModuleService service;

    public ModuleController(ModuleService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new module", description = "Creates a new module inside a specified track.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully created the module"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @PostMapping("/{trackId}/modules")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CourseModule createModule(
            @Parameter(description = "UUID of the track") @PathVariable UUID trackId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return service.createModule(trackId, request);
    }
}

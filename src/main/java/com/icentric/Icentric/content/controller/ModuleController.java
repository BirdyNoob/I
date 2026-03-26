package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateModuleRequest;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.service.ModuleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/content/tracks")
public class ModuleController {

    private final ModuleService service;

    public ModuleController(ModuleService service) {
        this.service = service;
    }

    @PostMapping("/{trackId}/modules")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CourseModule createModule(
            @PathVariable UUID trackId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return service.createModule(trackId, request);
    }
}

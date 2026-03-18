package com.icentric.Icentric.learning.controller;
import com.icentric.Icentric.learning.dto.LessonProgressRequest;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.service.LessonProgressService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
public class LessonProgressController {

    private final LessonProgressService service;

    public LessonProgressController(
            LessonProgressService service
    ) {
        this.service = service;
    }

    @PostMapping("/progress")
    @PreAuthorize("hasRole('LEARNER')")
    public LessonProgress updateProgress(
            @RequestBody LessonProgressRequest request
    ) {
        Authentication authentication = org.springframework.security.core.context
                .SecurityContextHolder
                .getContext()
                .getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new IllegalArgumentException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.updateProgress(userId, request);
    }
}

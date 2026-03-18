package com.icentric.Icentric.learning.controller;


import com.icentric.Icentric.learning.dto.LearnerAssignmentResponse;
import com.icentric.Icentric.learning.dto.NextLessonResponse;
import com.icentric.Icentric.learning.service.LearnerDashboardService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learner")
public class LearnerDashboardController {

    private final LearnerDashboardService service;

    public LearnerDashboardController(LearnerDashboardService service) {
        this.service = service;
    }

    @GetMapping("/assignments")
    public List<LearnerAssignmentResponse> assignments(Authentication authentication) {

        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new IllegalArgumentException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getAssignments(userId);
    }
    @GetMapping("/next-lesson")
    public NextLessonResponse nextLesson(Authentication authentication) {

        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new IllegalArgumentException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getNextLesson(userId);
    }
}

package com.icentric.Icentric.learning.controller;


import com.icentric.Icentric.learning.dto.CertificateResponse;
import com.icentric.Icentric.learning.dto.LearnerAssignmentResponse;
import com.icentric.Icentric.learning.dto.NextLessonResponse;
import com.icentric.Icentric.learning.service.CertificateService;
import com.icentric.Icentric.learning.service.LearnerDashboardService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learner")
public class LearnerDashboardController {

    private final LearnerDashboardService service;
    private final CertificateService certificateService;

    public LearnerDashboardController(LearnerDashboardService service, CertificateService certificateService) {
        this.service = service;
        this.certificateService = certificateService;
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
    @GetMapping("/certificates")
    public List<CertificateResponse> getCertificates(Authentication auth) {

        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new IllegalArgumentException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return certificateService.getCertificates(userId);
    }
}

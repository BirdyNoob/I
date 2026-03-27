package com.icentric.Icentric.learning.controller;


import com.icentric.Icentric.learning.dto.CertificateResponse;
import com.icentric.Icentric.learning.dto.CertificateDownloadResult;
import com.icentric.Icentric.learning.dto.LearnerAssignmentResponse;
import com.icentric.Icentric.learning.dto.LearnerDashboardResponse;
import com.icentric.Icentric.learning.dto.NextLessonResponse;
import com.icentric.Icentric.learning.service.CertificateService;
import com.icentric.Icentric.learning.service.LearnerDashboardService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getAssignments(userId);
    }
    @GetMapping("/next-lesson")
    public NextLessonResponse nextLesson(Authentication authentication) {

        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getNextLesson(userId);
    }
    @GetMapping("/certificates")
    public List<CertificateResponse> getCertificates(Authentication auth) {

        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return certificateService.getCertificates(userId);
    }
    @GetMapping("/dashboard")
    public LearnerDashboardResponse dashboard(
            Authentication auth
    ) {

        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getDashboard(userId);
    }
    @GetMapping("/certificates/{trackId}/download")
    public ResponseEntity<byte[]> download(

            @PathVariable UUID trackId,
            Authentication auth

    ) {
        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        CertificateDownloadResult certificate = certificateService.downloadCertificate(userId, trackId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + certificate.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(certificate.pdf());
    }
}

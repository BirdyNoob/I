package com.icentric.Icentric.learning.controller;


import com.icentric.Icentric.learning.dto.CertificateResponse;
import com.icentric.Icentric.learning.dto.CertificateDownloadResult;
import com.icentric.Icentric.learning.dto.LearnerAssignmentResponse;
import com.icentric.Icentric.learning.dto.LearnerDashboardResponse;
import com.icentric.Icentric.learning.dto.NextLessonResponse;
import com.icentric.Icentric.learning.service.CertificateService;
import com.icentric.Icentric.learning.service.LearnerDashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Learner Dashboard", description = "APIs for the learner's main dashboard, including assignments, lessons, and certificates")
public class LearnerDashboardController {

    private final LearnerDashboardService service;
    private final CertificateService certificateService;

    public LearnerDashboardController(LearnerDashboardService service, CertificateService certificateService) {
        this.service = service;
        this.certificateService = certificateService;
    }

    @Operation(summary = "Get current assignments", description = "Retrieves all active learning assignments for the authenticated learner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved assignments"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/assignments")
    public List<LearnerAssignmentResponse> assignments(Authentication authentication) {

        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getAssignments(userId);
    }

    @Operation(summary = "Get next recommended lesson", description = "Calculates and returns the best next lesson for the learner to resume learning.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the next lesson"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/next-lesson")
    public NextLessonResponse nextLesson(Authentication authentication) {

        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return service.getNextLesson(userId);
    }

    @Operation(summary = "Get earned certificates", description = "Retrieves a list of all certificates earned by the authenticated learner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved certificates"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/certificates")
    public List<CertificateResponse> getCertificates(Authentication auth) {

        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        return certificateService.getCertificates(userId);
    }

    @Operation(summary = "Get learner dashboard overview", description = "Retrieves aggregated progress and metrics for the learner's dashboard view.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard metrics"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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

    @Operation(summary = "Download a certificate", description = "Downloads a specific earned certificate as a PDF file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully downloaded the certificate PDF"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Certificate not found or not earned yet")
    })
    @GetMapping("/certificates/{trackId}/download")
    public ResponseEntity<byte[]> download(
            @Parameter(description = "UUID of the track for the certificate") @PathVariable UUID trackId,
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

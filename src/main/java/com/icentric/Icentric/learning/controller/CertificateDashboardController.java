package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.CertificateDashboardResponse;
import com.icentric.Icentric.learning.service.CertificateDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Learner-facing certificate dashboard.
 * GET /api/v1/learner/certificates/dashboard
 */
@RestController
@RequestMapping("/api/v1/learner/certificates")
@RequiredArgsConstructor
@Tag(name = "Certificates (Learner)", description = "APIs for learners to view their certificate progress and downloads")
public class CertificateDashboardController {

    private final CertificateDashboardService certificateDashboardService;

    @Operation(
            summary = "Get certificate dashboard",
            description = "Returns earned certificates with download/verify links, and in-progress certificate " +
                          "unlock progress for the authenticated learner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasRole('LEARNER')")
    @GetMapping("/dashboard")
    public ResponseEntity<CertificateDashboardResponse> getDashboard() {
        UUID userId = currentUserId();
        return ResponseEntity.ok(certificateDashboardService.getDashboard(userId));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException("Unauthenticated request");
        }
        return UUID.fromString(auth.getDetails().toString());
    }
}

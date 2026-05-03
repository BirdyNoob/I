package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.constants.CertificateStatus;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only endpoints for certificate recovery.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/admin/certificates/stuck}          – list all PENDING / FAILED records</li>
 *   <li>{@code POST /api/v1/admin/certificates/{id}/regenerate} – re-queue PDF generation for one record</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/certificates")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Certificate Recovery (Admin)", description = "APIs for diagnosing and recovering stuck or failed certificate generation")
public class AdminCertificateController {

    private final CertificateService certificateService;

    // ── 1. List stuck certificates ────────────────────────────────────────────

    @Operation(
            summary = "List stuck certificates",
            description = "Returns all issued certificate records within the current tenant that are in " +
                          "PENDING or FAILED state. Use this to identify certificates whose PDF generation " +
                          "did not complete successfully.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of stuck certificates"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/stuck")
    public ResponseEntity<List<StuckCertificateView>> listStuckCertificates() {
        List<IssuedCertificate> stuck = certificateService.listStuckCertificates();
        List<StuckCertificateView> views = stuck.stream()
                .map(StuckCertificateView::from)
                .toList();
        return ResponseEntity.ok(views);
    }

    // ── 2. Regenerate a single certificate ───────────────────────────────────

    @Operation(
            summary = "Regenerate a stuck certificate",
            description = "Re-queues PDF generation for an issued certificate record that is stuck in " +
                          "PENDING or FAILED state. The record is reset to PENDING and the async PDF " +
                          "pipeline is re-triggered. Returns 409 if the certificate is already READY.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Regeneration queued successfully"),
            @ApiResponse(responseCode = "404", description = "Issued certificate not found"),
            @ApiResponse(responseCode = "409", description = "Certificate is already READY — no action taken"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateCertificate(
            @Parameter(description = "UUID of the IssuedCertificate record to regenerate")
            @PathVariable UUID id
    ) {
        certificateService.regenerateCertificate(id);
        return ResponseEntity.ok(Map.of(
                "issuedCertificateId", id,
                "status", "PENDING",
                "message", "Certificate regeneration has been queued. Check status shortly."
        ));
    }

    // ── Response projection ───────────────────────────────────────────────────

    /**
     * Lightweight read-only view returned by the list endpoint.
     */
    public record StuckCertificateView(
            UUID id,
            UUID userId,
            UUID trackId,
            CertificateStatus status,
            Instant issuedAt,
            Instant generatedAt,
            String generationError
    ) {
        static StuckCertificateView from(IssuedCertificate ic) {
            return new StuckCertificateView(
                    ic.getId(),
                    ic.getUserId(),
                    ic.getTrackId(),
                    ic.getStatus(),
                    ic.getIssuedAt(),
                    ic.getGeneratedAt(),
                    ic.getGenerationError()
            );
        }
    }
}

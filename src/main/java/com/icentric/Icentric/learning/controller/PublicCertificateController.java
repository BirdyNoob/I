package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.CertificateDownloadResult;
import com.icentric.Icentric.learning.dto.CertificateVerificationResponse;
import com.icentric.Icentric.learning.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/certificates")
@Tag(name = "Public Certificate Verification", description = "Public APIs to verify and download issued learner certificates.")
public class PublicCertificateController {

    private final CertificateService certificateService;

    public PublicCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Operation(summary = "Verify certificate", description = "Allows any party to verify an issued certificate by certificate ID, optionally using the verification token from the public link.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Certificate verification result returned"),
            @ApiResponse(responseCode = "404", description = "Certificate not found")
    })
    @GetMapping("/{certificateId}/verify")
    public CertificateVerificationResponse verifyCertificate(
            @Parameter(description = "Issued certificate UUID") @PathVariable UUID certificateId,
            @Parameter(description = "Verification token from the public verification link") @RequestParam(required = false) UUID token
    ) {
        return certificateService.verifyCertificate(certificateId, token);
    }

    @Operation(summary = "Download certificate", description = "Downloads the already-generated certificate PDF using its public verification link token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Certificate PDF downloaded"),
            @ApiResponse(responseCode = "404", description = "Certificate not found"),
            @ApiResponse(responseCode = "409", description = "Certificate generation is still in progress")
    })
    @GetMapping("/{certificateId}/download")
    public ResponseEntity<byte[]> downloadCertificate(
            @Parameter(description = "Issued certificate UUID") @PathVariable UUID certificateId,
            @Parameter(description = "Verification token from the public verification link") @RequestParam UUID token
    ) {
        CertificateDownloadResult certificate = certificateService.downloadCertificatePublic(certificateId, token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + certificate.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(certificate.pdf());
    }
}

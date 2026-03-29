package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.CertificateStatus;

import java.time.Instant;
import java.util.UUID;

public record CertificateVerificationResponse(
        UUID certificateId,
        UUID verificationToken,
        String learnerName,
        String learnerEmail,
        String trackTitle,
        Instant issuedAt,
        Instant generatedAt,
        CertificateStatus status,
        boolean valid,
        String downloadUrl
) {}

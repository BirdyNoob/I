package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.CertificateStatus;

import java.time.Instant;
import java.util.UUID;

public record CertificateVerificationData(
        UUID issuedCertificateId,
        UUID verificationToken,
        UUID userId,
        String userName,
        String userEmail,
        UUID trackId,
        String trackTitle,
        Instant issuedAt,
        Instant generatedAt,
        CertificateStatus status,
        String downloadUrl
) {}

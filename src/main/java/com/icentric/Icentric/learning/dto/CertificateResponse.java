package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.CertificateStatus;

import java.time.Instant;
import java.util.UUID;

public record CertificateResponse(
        UUID certificateId,
        String title,
        UUID trackId,
        Instant issuedAt,
        CertificateStatus status,
        String downloadUrl,
        String verificationUrl
) {}

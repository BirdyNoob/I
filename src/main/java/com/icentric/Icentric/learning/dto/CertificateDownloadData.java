package com.icentric.Icentric.learning.dto;

import java.util.UUID;
import java.time.Instant;

public record CertificateDownloadData(
        UUID certificateId,
        UUID verificationToken,
        UUID learnerId,
        String userName,
        String userEmail,
        String trackTitle,
        Instant issuedAt
) {}

package com.icentric.Icentric.learning.dto;

import java.time.Instant;

public record CertificateDownloadData(
        String userEmail,
        String trackTitle,
        Instant issuedAt
) {}

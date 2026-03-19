package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record CertificateResponse(
        String title,
        UUID trackId,
        Instant issuedAt
) {}

package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record CertificateItem(

        UUID trackId,
        String title,
        Instant issuedAt

) {}

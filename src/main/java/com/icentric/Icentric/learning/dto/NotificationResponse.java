package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(

        UUID id,
        String type,
        String message,
        Boolean isRead,
        Instant createdAt
) {}
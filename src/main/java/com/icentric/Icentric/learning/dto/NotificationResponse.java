package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(

        UUID id,
        NotificationType type,
        String message,
        Boolean isRead,
        Instant createdAt
) {}

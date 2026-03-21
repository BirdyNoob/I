package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminNotificationResponse(

        UUID notificationId,
        UUID userId,
        String userEmail,
        String type,
        String message,
        boolean sent,
        Instant createdAt

) {}

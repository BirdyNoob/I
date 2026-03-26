package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record AdminNotificationResponse(

        UUID notificationId,
        UUID userId,
        String userEmail,
        NotificationType type,
        String message,
        boolean sent,
        Instant createdAt

) {}

package com.icentric.Icentric.learning.entity;

import com.icentric.Icentric.learning.constants.NotificationType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "notification_events")
public class NotificationEvent {

    @Id
    private UUID id;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    private String eventKey;
    private String message;
    private Boolean sent;
    private Boolean isRead = false;
    private Instant createdAt;
}

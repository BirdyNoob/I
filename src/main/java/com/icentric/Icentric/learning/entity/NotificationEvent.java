package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
    private String type; // OVERDUE, FAILED
    private String message;
    private Boolean sent;
    private Boolean isRead = false;
    private Instant createdAt;
}
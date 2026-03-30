package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "assignment_notification_configs", schema = "system")
public class AssignmentNotificationConfig {

    @Id
    private UUID id;

    private UUID tenantId;
    private Boolean reminderEnabled;
    private String reminderOffsetsHours;
    private Boolean escalationEnabled;
    private Integer escalationDelayHours;
    private Instant createdAt;
    private Instant updatedAt;
}

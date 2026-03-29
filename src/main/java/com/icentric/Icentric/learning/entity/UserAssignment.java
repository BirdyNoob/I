package com.icentric.Icentric.learning.entity;


import com.icentric.Icentric.learning.constants.AssignmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "user_assignments", indexes = {
    @jakarta.persistence.Index(name = "idx_user_assignments_user_id", columnList = "user_id")
})
public class UserAssignment {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "track_id")
    private UUID trackId;

    private Instant assignedAt;

    private Instant dueDate;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status;

    private Integer contentVersionAtAssignment;
    private Boolean requiresRetraining;

    public UserAssignment() {}

    // getters setters
}

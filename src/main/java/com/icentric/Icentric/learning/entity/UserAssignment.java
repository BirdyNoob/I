package com.icentric.Icentric.learning.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "user_assignments")
public class UserAssignment {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "track_id")
    private UUID trackId;

    private Instant assignedAt;

    private Instant dueDate;

    private String status;

    private Integer contentVersionAtAssignment;

    public UserAssignment() {}

    // getters setters
}

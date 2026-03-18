package com.icentric.Icentric.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    private String status;

    private Instant completedAt;

    private Boolean requiresRetraining;

    private Instant createdAt;

    public LessonProgress() {}

    // getters setters
}

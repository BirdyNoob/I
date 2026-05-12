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

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "completed_step_ids", columnDefinition = "jsonb")
    private java.util.List<UUID> completedStepIds = new java.util.ArrayList<>();

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "simulation_scores", columnDefinition = "jsonb")
    private java.util.Map<String, Integer> simulationScores = new java.util.HashMap<>();

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "decision_history", columnDefinition = "jsonb")
    private java.util.Map<String, Object> decisionHistory = new java.util.HashMap<>();

    public LessonProgress() {}

    // getters setters
}

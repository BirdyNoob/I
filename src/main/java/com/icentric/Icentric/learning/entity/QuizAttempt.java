package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;


import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    private UUID id;

    private UUID userId;
    private UUID lessonId;

    private int score;
    private int totalQuestions;

    private Instant attemptedAt;
    private Boolean passed;
    private Integer attemptNumber;
}

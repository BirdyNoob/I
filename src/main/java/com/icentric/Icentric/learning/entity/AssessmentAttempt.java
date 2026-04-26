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
@Table(name = "assessment_attempts")
public class AssessmentAttempt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assessment_config_id", nullable = false)
    private String assessmentConfigId;
    
    @Column(nullable = false)
    private String status; // PASSED, FAILED
    
    private Integer score;
    
    @Column(name = "attempt_number")
    private Integer attemptNumber;
    
    @Column(name = "date_completed")
    private Instant dateCompleted;
    
    @Column(name = "questions_answered")
    private Integer questionsAnswered;
    
    @Column(name = "total_questions")
    private Integer totalQuestions;
    
    @Column(name = "certificate_id")
    private String certificateId;
}

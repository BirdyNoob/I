package com.icentric.Icentric.learning.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "assessments", schema = "system")
public class Assessment {

    @Id
    private String id;

    @Column(name = "track_id", nullable = false)
    private String trackId;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    @Column(name = "track_name")
    private String trackName;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions = 50;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds = 3600;

    @Column(name = "passing_score", nullable = false)
    private int passingScore = 80;

    @Column(name = "retake_policy", nullable = false)
    private String retakePolicy = "UNLIMITED";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<AssessmentSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<AssessmentQuestion> questions = new ArrayList<>();
}

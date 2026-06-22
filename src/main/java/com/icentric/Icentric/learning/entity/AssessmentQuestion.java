package com.icentric.Icentric.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "assessment_questions", schema = "system",
       uniqueConstraints = @UniqueConstraint(columnNames = {"assessment_id", "question_id"}))
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private AssessmentSection section;

    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;

    @Column(nullable = false, length = 50)
    private String type = "MULTIPLE_CHOICE";

    private String topic;

    private Integer difficulty;

    @Column(name = "scenario_context")
    private String scenarioContext;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "correct_option_id", nullable = false, length = 100)
    private String correctOptionId;

    private String explanation;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<AssessmentOption> options = new ArrayList<>();

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private AssessmentImage image;
}

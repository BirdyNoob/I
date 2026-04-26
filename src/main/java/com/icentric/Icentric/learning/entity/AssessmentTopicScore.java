package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "assessment_topic_scores")
public class AssessmentTopicScore {

    @Id
    private UUID id;

    private UUID assessmentAttemptId;
    private String name;
    private Integer score;
}

package com.icentric.Icentric.content.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "tracks", schema = "system")
public class Track {

    @Id
    private UUID id;

    private String slug;

    private String title;

    private String description;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private com.icentric.Icentric.common.enums.Department department;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(name = "course_type")
    private com.icentric.Icentric.content.constants.CourseType courseType;

    private Integer estimatedMins;

    private Integer version;

    @jakarta.persistence.Column(name = "version_label")
    private String versionLabel;

    private UUID previousVersionId;

    private Boolean isPublished;

    private Boolean isMandatory;

    private Instant createdAt;
    private Instant publishedAt;
    private String status;
    private String changeSummary;

}

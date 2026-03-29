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

    private String department;

    private String trackType;

    private Integer estimatedMins;

    private Integer version;

    private UUID previousVersionId;

    private Boolean isPublished;

    private Instant createdAt;
    private Instant publishedAt;
    private String status;
    private String changeSummary;

}

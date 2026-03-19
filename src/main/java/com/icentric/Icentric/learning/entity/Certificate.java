package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "certificates", schema = "system")
public class Certificate {

    @Id
    private UUID id;

    private UUID trackId;
    private String title;
    private String description;
    private Instant createdAt;
}
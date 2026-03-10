package com.icentric.Icentric.platform.tenant.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "tenants", schema = "system")
public class Tenant {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String slug;

    private String companyName;

    private String plan;

    private Integer maxSeats;

    private String status;

    private Instant createdAt;

    private Instant trialEndsAt;

    public Tenant() {}

    public Tenant(String slug, String companyName) {
        this.id = UUID.randomUUID();
        this.slug = slug;
        this.companyName = companyName;
        this.status = "active";
        this.createdAt = Instant.now();
    }

    // getters and setters
}

package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "issued_certificates")
public class IssuedCertificate {

    @Id
    private UUID id;

    private UUID userId;
    private UUID trackId;
    private UUID certificateId;
    private Instant issuedAt;
}

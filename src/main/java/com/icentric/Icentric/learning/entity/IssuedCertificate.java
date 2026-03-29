package com.icentric.Icentric.learning.entity;

import com.icentric.Icentric.learning.constants.CertificateStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;
    private String fileName;
    private String blobPath;
    private String downloadUrl;
    private UUID verificationToken;
    private Instant issuedAt;
    private Instant generatedAt;
    private String generationError;
}

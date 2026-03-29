package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.learning.dto.CertificateDownloadData;
import com.icentric.Icentric.learning.constants.CertificateStatus;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CertificateIssuanceAsyncService {

    private final IssuedCertificateRepository issuedCertificateRepository;
    private final CertificatePdfService certificatePdfService;
    private final CertificateStorageService certificateStorageService;
    private final CertificateUrlService certificateUrlService;
    private final TenantSchemaService tenantSchemaService;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public CertificateIssuanceAsyncService(
            IssuedCertificateRepository issuedCertificateRepository,
            CertificatePdfService certificatePdfService,
            CertificateStorageService certificateStorageService,
            CertificateUrlService certificateUrlService,
            TenantSchemaService tenantSchemaService,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.certificatePdfService = certificatePdfService;
        this.certificateStorageService = certificateStorageService;
        this.certificateUrlService = certificateUrlService;
        this.tenantSchemaService = tenantSchemaService;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    @Async
    @Transactional
    public void generateAndStore(UUID issuedCertificateId, String tenantSlug) {
        TenantContext.setTenant(tenantSlug);
        try {
            tenantSchemaService.applyCurrentTenantSearchPath();

            IssuedCertificate issued = issuedCertificateRepository.findById(issuedCertificateId)
                    .orElseThrow(() -> new NoSuchElementException("Issued certificate not found: " + issuedCertificateId));

            CertificateDownloadData data = issuedCertificateRepository
                    .findCertificateDownloadData(issued.getUserId(), issued.getTrackId())
                    .orElseThrow(() -> new NoSuchElementException("Certificate payload not found for issued certificate: " + issuedCertificateId));

            byte[] pdf = certificatePdfService.generateCertificate(data);
            String fileName = buildCertificateFilename(data);
            String storageKey = "tenant-" + tenantSlug + "/" + issuedCertificateId + "/" + fileName;
            String blobPath = certificateStorageService.store(storageKey, pdf);

            issued.setFileName(fileName);
            issued.setBlobPath(blobPath);
            issued.setGeneratedAt(Instant.now());
            issued.setStatus(CertificateStatus.READY);
            issued.setDownloadUrl(certificateUrlService.downloadUrl(issued.getId(), issued.getVerificationToken()));
            issued.setGenerationError(null);
            issuedCertificateRepository.save(issued);
            auditService.log(
                    issued.getUserId(),
                    AuditAction.CERTIFICATE_READY,
                    "CERTIFICATE",
                    issued.getId().toString(),
                    "Certificate is ready for "
                            + auditMetadataService.describeUserInCurrentTenant(issued.getUserId())
                            + ". Download URL: " + issued.getDownloadUrl()
            );
        } catch (Exception e) {
            failCertificate(issuedCertificateId, e.getMessage(), tenantSlug);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    protected void failCertificate(UUID issuedCertificateId, String error, String tenantSlug) {
        TenantContext.setTenant(tenantSlug);
        try {
            tenantSchemaService.applyCurrentTenantSearchPath();
            issuedCertificateRepository.findById(issuedCertificateId).ifPresent(issued -> {
                issued.setStatus(CertificateStatus.FAILED);
                issued.setGenerationError(error);
                issuedCertificateRepository.save(issued);
                auditService.log(
                        issued.getUserId(),
                        AuditAction.CERTIFICATE_GENERATION_FAILED,
                        "CERTIFICATE",
                        issued.getId().toString(),
                        "Certificate generation failed for "
                                + auditMetadataService.describeUserInCurrentTenant(issued.getUserId())
                                + ": " + error
                );
            });
        } finally {
            TenantContext.clear();
        }
    }

    private String buildCertificateFilename(CertificateDownloadData data) {
        String userIdentifier = data.userName() != null && !data.userName().isBlank()
                ? data.userName()
                : data.userEmail();
        return "certificate-" + sanitizeFilenamePart(userIdentifier) + "-" + sanitizeFilenamePart(data.trackTitle()) + ".pdf";
    }

    private String sanitizeFilenamePart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String sanitized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return sanitized.isEmpty() ? "unknown" : sanitized;
    }
}

package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.service.TenantAccessGuard;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.constants.CertificateStatus;
import com.icentric.Icentric.learning.dto.CertificateDownloadResult;
import com.icentric.Icentric.learning.dto.CertificateResponse;
import com.icentric.Icentric.learning.dto.CertificateVerificationData;
import com.icentric.Icentric.learning.dto.CertificateVerificationResponse;
import com.icentric.Icentric.learning.entity.Certificate;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final IssuedCertificateRepository issuedRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final TrackRepository trackRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;
    private final CertificateStorageService certificateStorageService;
    private final CertificateUrlService certificateUrlService;
    private final CertificateIssuanceAsyncService certificateIssuanceAsyncService;
    private final TenantSchemaService tenantSchemaService;
    private final TenantRepository tenantRepository;
    private final TenantAccessGuard tenantAccessGuard;

    public CertificateService(
            CertificateRepository certificateRepository,
            IssuedCertificateRepository issuedRepository,
            LessonProgressRepository progressRepository,
            LessonRepository lessonRepository,
            UserAssignmentRepository assignmentRepository,
            TrackRepository trackRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService,
            CertificateStorageService certificateStorageService,
            CertificateUrlService certificateUrlService,
            CertificateIssuanceAsyncService certificateIssuanceAsyncService,
            TenantSchemaService tenantSchemaService,
            TenantRepository tenantRepository,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.certificateRepository = certificateRepository;
        this.issuedRepository = issuedRepository;
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.assignmentRepository = assignmentRepository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
        this.certificateStorageService = certificateStorageService;
        this.certificateUrlService = certificateUrlService;
        this.certificateIssuanceAsyncService = certificateIssuanceAsyncService;
        this.tenantSchemaService = tenantSchemaService;
        this.tenantRepository = tenantRepository;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional
    public void checkAndIssue(UUID userId, UUID trackId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        IssuedCertificate existing = issuedRepository.findByUserIdAndTrackId(userId, trackId).orElse(null);
        if (existing != null && existing.getStatus() != CertificateStatus.FAILED) {
            return;
        }

        long completed =
                progressRepository.countCompletedLessons(userId, trackId);

        long total = lessonRepository.countLessonsInTrack(trackId);

        // A track is ready for certificate if all lessons are completed.
        // If total is 0, it's a special track (e.g. assessment-only) and we allow issuance.
        boolean allLessonsDone = (total == 0) || (completed >= total);

        if (allLessonsDone) {

            var certificate = certificateRepository
                    .findByTrackId(trackId)
                    .orElseGet(() -> {
                        Certificate newCert = new Certificate();
                        newCert.setId(UUID.randomUUID());
                        newCert.setTrackId(trackId);
                        var track = trackRepository.findById(trackId).orElse(null);
                        newCert.setTitle(track != null ? track.getTitle() + " Certificate" : "Certificate of Completion");
                        newCert.setDescription("Awarded for successful completion of the track requirements.");
                        newCert.setCreatedAt(Instant.now());
                        return certificateRepository.save(newCert);
                    });

            IssuedCertificate issued = existing != null ? existing : new IssuedCertificate();
            if (issued.getId() == null) {
                issued.setId(UUID.randomUUID());
            }
            issued.setUserId(userId);
            issued.setTrackId(trackId);
            issued.setCertificateId(certificate.getId());
            issued.setIssuedAt(existing != null && existing.getIssuedAt() != null ? existing.getIssuedAt() : Instant.now());
            issued.setStatus(CertificateStatus.PENDING);
            issued.setVerificationToken(existing != null && existing.getVerificationToken() != null
                    ? existing.getVerificationToken()
                    : UUID.randomUUID());
            issued.setBlobPath(null);
            issued.setFileName(null);
            issued.setDownloadUrl(certificateUrlService.downloadUrl(issued.getId(), issued.getVerificationToken()));
            issued.setGeneratedAt(null);
            issued.setGenerationError(null);

            issuedRepository.save(issued);
            String tenantSlug = currentTenantSlug();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    certificateIssuanceAsyncService.generateAndStore(issued.getId(), tenantSlug);
                }
            });

            auditService.log(
                    userId,
                    AuditAction.CERTIFICATE_ISSUED,
                    "CERTIFICATE",
                    certificate.getId().toString(),
                    "Issued certificate to "
                            + auditMetadataService.describeUserInCurrentTenant(userId)
                            + " for " + auditMetadataService.describeTrack(trackId)
                            + ". Generation queued asynchronously."
            );
            // ── Update assignment status if it exists ────────────────────────
            assignmentRepository.findByUserIdAndTrackId(userId, trackId)
                    .ifPresent(assignment -> {
                        var track = trackRepository.findById(trackId).orElse(null);
                        if (track != null) {
                            assignment.setContentVersionAtAssignment(track.getVersion());
                        }
                        assignment.setRequiresRetraining(false);
                        assignment.setStatus(AssignmentStatus.COMPLETED);
                        assignmentRepository.save(assignment);
                    });
        }

    }
    @Transactional(readOnly = true)
    public List<CertificateResponse> getCertificates(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        tenantAccessGuard.assertUserBelongsToCurrentTenant(userId);

        return issuedRepository.findByUserId(userId)
                .stream()
                .map(ic -> {

                    var cert = certificateRepository
                            .findById(ic.getCertificateId())
                            .orElseThrow(() -> new NoSuchElementException(
                                    "Certificate definition not found with id: " + ic.getCertificateId()
                            ));

                    return new CertificateResponse(
                            ic.getId(),
                            cert.getTitle(),
                            ic.getTrackId(),
                            ic.getIssuedAt(),
                            ic.getStatus(),
                            ic.getDownloadUrl(),
                            certificateUrlService.verificationUrl(ic.getId(), ic.getVerificationToken())
                    );

                }).toList();
    }
    @Transactional(readOnly = true)
    public CertificateDownloadResult downloadCertificate(UUID userId, UUID trackId) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        tenantAccessGuard.assertUserBelongsToCurrentTenant(userId);

        IssuedCertificate issued = issuedRepository.findByUserIdAndTrackId(userId, trackId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Issued certificate not found for userId: " + userId + " and trackId: " + trackId
                ));

        if (issued.getStatus() != CertificateStatus.READY || issued.getBlobPath() == null) {
            throw new IllegalStateException("Certificate is not ready for download yet");
        }

        return new CertificateDownloadResult(
                issued.getFileName(),
                certificateStorageService.load(issued.getBlobPath())
        );
    }

    @Transactional(readOnly = true)
    public CertificateVerificationResponse verifyCertificate(UUID certificateId, UUID verificationToken) {
        CertificateVerificationData data = locateCertificateData(certificateId, verificationToken)
                .orElseThrow(() -> new NoSuchElementException("Certificate not found"));

        boolean valid = data.status() == CertificateStatus.READY;
        return new CertificateVerificationResponse(
                data.issuedCertificateId(),
                data.verificationToken(),
                data.userName(),
                data.userEmail(),
                data.trackTitle(),
                data.issuedAt(),
                data.generatedAt(),
                data.status(),
                valid,
                data.downloadUrl()
        );
    }

    @Transactional(readOnly = true)
    public CertificateDownloadResult downloadCertificatePublic(UUID certificateId, UUID verificationToken) {
        LocatedCertificate located = locateIssuedCertificate(certificateId, verificationToken)
                .orElseThrow(() -> new NoSuchElementException("Certificate not found"));

        if (located.issuedCertificate().getStatus() != CertificateStatus.READY
                || located.issuedCertificate().getBlobPath() == null) {
            throw new IllegalStateException("Certificate is not ready for download yet");
        }

        return new CertificateDownloadResult(
                located.issuedCertificate().getFileName(),
                certificateStorageService.load(located.issuedCertificate().getBlobPath())
        );
    }

    private Optional<CertificateVerificationData> locateCertificateData(UUID certificateId, UUID verificationToken) {
        return scanTenants(tenantSlug -> {
            tenantSchemaService.applyCurrentTenantSearchPath();
            if (verificationToken != null) {
                return issuedRepository.findVerificationDataByToken(verificationToken)
                        .filter(data -> certificateId == null || data.issuedCertificateId().equals(certificateId));
            }
            if (certificateId != null) {
                return issuedRepository.findVerificationDataById(certificateId);
            }
            return Optional.empty();
        });
    }

    private Optional<LocatedCertificate> locateIssuedCertificate(UUID certificateId, UUID verificationToken) {
        return scanTenants(tenantSlug -> {
            tenantSchemaService.applyCurrentTenantSearchPath();
            if (verificationToken != null) {
                return issuedRepository.findByVerificationToken(verificationToken)
                        .filter(issued -> certificateId == null || issued.getId().equals(certificateId))
                        .map(issued -> new LocatedCertificate(tenantSlug, issued));
            }
            if (certificateId != null) {
                return issuedRepository.findById(certificateId)
                        .map(issued -> new LocatedCertificate(tenantSlug, issued));
            }
            return Optional.empty();
        });
    }

    private <T> Optional<T> scanTenants(java.util.function.Function<String, Optional<T>> lookup) {
        String originalTenant = TenantContext.getTenant();
        try {
            for (Tenant tenant : tenantRepository.findAll()) {
                TenantContext.setTenant(tenant.getSlug());
                Optional<T> result = lookup.apply(tenant.getSlug());
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        } finally {
            if (originalTenant != null && !originalTenant.isBlank()) {
                TenantContext.setTenant(originalTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String currentTenantSlug() {
        return tenantAccessGuard.currentTenantSlug();
    }

    private record LocatedCertificate(String tenantSlug, IssuedCertificate issuedCertificate) {
    }
}

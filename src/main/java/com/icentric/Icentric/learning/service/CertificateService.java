package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.CertificateDownloadData;
import com.icentric.Icentric.learning.dto.CertificateDownloadResult;
import com.icentric.Icentric.learning.dto.CertificateResponse;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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
    private final CertificatePdfService pdfService;
    private final TenantSchemaService tenantSchemaService;

    public CertificateService(
            CertificateRepository certificateRepository,
            IssuedCertificateRepository issuedRepository,
            LessonProgressRepository progressRepository,
            LessonRepository lessonRepository,
            UserAssignmentRepository assignmentRepository,
            TrackRepository trackRepository,
            AuditService auditService,
            CertificatePdfService pdfService,
            TenantSchemaService tenantSchemaService
    ) {
        this.certificateRepository = certificateRepository;
        this.issuedRepository = issuedRepository;
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.assignmentRepository = assignmentRepository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.pdfService = pdfService;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Transactional
    public void checkAndIssue(UUID userId, UUID trackId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        boolean alreadyIssued =
                issuedRepository.existsByUserIdAndTrackId(userId, trackId);

        if (alreadyIssued) return;

        long completed =
                progressRepository.countCompletedLessons(userId, trackId);

        long total =
                lessonRepository.countLessonsInTrack(trackId);

        if (completed == total && total > 0) {

            var certificate = certificateRepository
                    .findByTrackId(trackId)
                    .orElse(null);

            if (certificate == null) return;

            IssuedCertificate issued = new IssuedCertificate();
            issued.setId(UUID.randomUUID());
            issued.setUserId(userId);
            issued.setTrackId(trackId);
            issued.setCertificateId(certificate.getId());
            issued.setIssuedAt(Instant.now());

            issuedRepository.save(issued);

            auditService.log(userId, "CERTIFICATE_ISSUED", "CERTIFICATE", certificate.getId().toString(), "Certificate issued for track " + trackId);
            var assignment = assignmentRepository
                    .findByUserIdAndTrackId(userId, trackId)
                    .orElseThrow();

            var track = trackRepository.findById(trackId)
                    .orElseThrow();

// 🔥 RESET RETRAINING STATE
            assignment.setContentVersionAtAssignment(track.getVersion());
            assignment.setRequiresRetraining(false);
            assignment.setStatus(AssignmentStatus.COMPLETED);

            assignmentRepository.save(assignment);
        }

    }
    @Transactional(readOnly = true)
    public List<CertificateResponse> getCertificates(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        return issuedRepository.findByUserId(userId)
                .stream()
                .map(ic -> {

                    var cert = certificateRepository
                            .findById(ic.getCertificateId())
                            .orElseThrow(() -> new NoSuchElementException(
                                    "Certificate definition not found with id: " + ic.getCertificateId()
                            ));

                    return new CertificateResponse(
                            cert.getTitle(),
                            ic.getTrackId(),
                            ic.getIssuedAt()
                    );

                }).toList();
    }
    @Transactional(readOnly = true)
    public CertificateDownloadResult downloadCertificate(UUID userId, UUID trackId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        CertificateDownloadData data = issuedRepository.findCertificateDownloadData(userId, trackId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Issued certificate not found for userId: " + userId + " and trackId: " + trackId
                ));

        return new CertificateDownloadResult(
                buildCertificateFilename(data),
                pdfService.generateCertificate(data)
        );
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

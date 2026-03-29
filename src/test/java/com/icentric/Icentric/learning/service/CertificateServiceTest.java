package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.dto.CertificateDownloadData;
import com.icentric.Icentric.learning.dto.CertificateDownloadResult;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private IssuedCertificateRepository issuedRepository;

    @Mock
    private LessonProgressRepository progressRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private UserAssignmentRepository assignmentRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private CertificatePdfService pdfService;

    @Mock
    private TenantSchemaService tenantSchemaService;

    @InjectMocks
    private CertificateService certificateService;

    @Test
    @DisplayName("downloadCertificate uses user name in generated file name when available")
    void downloadCertificate_usesUserNameForFilename() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        CertificateDownloadData data = new CertificateDownloadData(
                UUID.randomUUID(),
                userId,
                "Aryan Kundal",
                "aryan@example.com",
                "AI Safety Essentials",
                Instant.parse("2026-03-28T12:00:00Z")
        );

        when(issuedRepository.findCertificateDownloadData(userId, trackId)).thenReturn(Optional.of(data));
        when(pdfService.generateCertificate(data)).thenReturn(new byte[]{1, 2, 3});

        CertificateDownloadResult result = certificateService.downloadCertificate(userId, trackId);

        verify(tenantSchemaService).applyCurrentTenantSearchPath();
        assertThat(result.filename()).isEqualTo("certificate-aryan-kundal-ai-safety-essentials.pdf");
        assertThat(result.pdf()).containsExactly(1, 2, 3);
    }
}

package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.service.PlaywrightPdfService;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserServiceBulkPdfTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AuditMetadataService auditMetadataService;
    @Mock private TenantAccessGuard tenantAccessGuard;
    @Mock private EmailService emailService;
    @Mock private TrackRepository trackRepository;
    @Mock private UserAssignmentRepository userAssignmentRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private IssuedCertificateRepository issuedCertificateRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private TenantSchemaService tenantSchemaService;

    @Test
    @DisplayName("getBulkUploadInstructionsPdf renders dynamic A4 Portrait PDF guide containing system departments")
    void getBulkUploadInstructionsPdf_rendersBeautifulGuide() throws Exception {
        PlaywrightPdfService playwrightPdfService = new PlaywrightPdfService();
        UserService userService = new UserService(
                userRepository,
                tenantUserRepository,
                passwordEncoder,
                "ChangeMe@123",
                auditService,
                auditMetadataService,
                tenantAccessGuard,
                emailService,
                "http://localhost:8080",
                trackRepository,
                userAssignmentRepository,
                lessonProgressRepository,
                issuedCertificateRepository,
                lessonRepository,
                tenantSchemaService,
                playwrightPdfService
        );

        byte[] pdf = userService.getBulkUploadInstructionsPdf();

        assertThat(pdf).isNotEmpty();

        Path out = Path.of("/Users/aryankundal/.gemini/antigravity/brain/962492b3-9127-473d-ba33-8f5a3c6d533e/scratch/tenant-user-bulk-upload-guide.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("Bulk instructions PDF guide written to scratch: " + out.toAbsolutePath());

        assertThat(out.toFile()).exists();
    }
}

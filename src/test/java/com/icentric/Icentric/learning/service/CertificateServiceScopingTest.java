package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.service.TenantAccessGuard;
import com.icentric.Icentric.learning.constants.CertificateStatus;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceScopingTest {

    @Mock private CertificateRepository certificateRepository;
    @Mock private IssuedCertificateRepository issuedRepository;
    @Mock private LessonProgressRepository progressRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserAssignmentRepository assignmentRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private AuditService auditService;
    @Mock private AuditMetadataService auditMetadataService;
    @Mock private CertificateStorageService certificateStorageService;
    @Mock private CertificateUrlService certificateUrlService;
    @Mock private CertificateIssuanceAsyncService certificateIssuanceAsyncService;
    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantAccessGuard tenantAccessGuard;
    @Mock private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private CertificateService certificateService;

    private UUID managerId;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        managerId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug("test-tenant");
        TenantContext.setTenant("test-tenant");
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        TransactionSynchronizationManager.clear();
    }

    private void setSecurityContext(UUID userId) {
        Authentication auth = mock(Authentication.class);
        when(auth.getDetails()).thenReturn(userId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("ROLE_SUPER_ADMIN retains full, unfiltered access to all stuck certificates")
    void superAdminStuckCertificates() {
        setSecurityContext(managerId);

        TenantUser superAdmin = new TenantUser();
        superAdmin.setUserId(managerId);
        superAdmin.setRole("SUPER_ADMIN");

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(superAdmin));

        IssuedCertificate stuck1 = new IssuedCertificate();
        stuck1.setId(UUID.randomUUID());
        stuck1.setUserId(UUID.randomUUID()); // other user
        stuck1.setStatus(CertificateStatus.FAILED);

        IssuedCertificate stuck2 = new IssuedCertificate();
        stuck2.setId(UUID.randomUUID());
        stuck2.setUserId(managerId);
        stuck2.setStatus(CertificateStatus.PENDING);

        when(issuedRepository.findByStatus(CertificateStatus.PENDING)).thenReturn(List.of(stuck2));
        when(issuedRepository.findByStatus(CertificateStatus.FAILED)).thenReturn(List.of(stuck1));

        List<IssuedCertificate> stuck = certificateService.listStuckCertificates();
        assertThat(stuck).hasSize(2);
    }

    @Test
    @DisplayName("ROLE_ADMIN (standard manager) is restricted to only viewing their own or their onboarded users' stuck certificates")
    void standardAdminScopedStuckCertificates() {
        setSecurityContext(managerId);

        TenantUser admin = new TenantUser();
        admin.setUserId(managerId);
        admin.setRole("ADMIN");

        UUID onboardedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(admin));
        when(tenantUserRepository.findUserIdsByTenantIdAndCreatedBy(tenantId, managerId))
                .thenReturn(List.of(onboardedUserId));

        IssuedCertificate stuckOther = new IssuedCertificate();
        stuckOther.setId(UUID.randomUUID());
        stuckOther.setUserId(otherUserId);
        stuckOther.setStatus(CertificateStatus.FAILED);

        IssuedCertificate stuckOnboarded = new IssuedCertificate();
        stuckOnboarded.setId(UUID.randomUUID());
        stuckOnboarded.setUserId(onboardedUserId);
        stuckOnboarded.setStatus(CertificateStatus.PENDING);

        when(issuedRepository.findByStatus(CertificateStatus.PENDING)).thenReturn(List.of(stuckOnboarded));
        when(issuedRepository.findByStatus(CertificateStatus.FAILED)).thenReturn(List.of(stuckOther));

        List<IssuedCertificate> stuck = certificateService.listStuckCertificates();

        // Should filter out stuckOther and only return stuckOnboarded
        assertThat(stuck).hasSize(1);
        assertThat(stuck.get(0).getUserId()).isEqualTo(onboardedUserId);
    }

    @Test
    @DisplayName("ROLE_ADMIN throws AccessDeniedException when attempting to regenerate a certificate for an unauthorized user")
    void standardAdminRegenerateUnauthorizedThrowsException() {
        setSecurityContext(managerId);

        TenantUser admin = new TenantUser();
        admin.setUserId(managerId);
        admin.setRole("ADMIN");

        UUID otherUserId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(admin));
        when(tenantUserRepository.findUserIdsByTenantIdAndCreatedBy(tenantId, managerId))
                .thenReturn(List.of(UUID.randomUUID())); // onboarded user is different

        IssuedCertificate cert = new IssuedCertificate();
        cert.setId(certificateId);
        cert.setUserId(otherUserId);
        cert.setStatus(CertificateStatus.FAILED);

        when(issuedRepository.findById(certificateId)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.regenerateCertificate(certificateId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized to regenerate certificates for this user");
    }

    @Test
    @DisplayName("ROLE_ADMIN successfully regenerates stuck certificate for their onboarded user")
    void standardAdminRegenerateAuthorizedSucceeds() {
        setSecurityContext(managerId);

        TenantUser admin = new TenantUser();
        admin.setUserId(managerId);
        admin.setRole("ADMIN");

        UUID onboardedUserId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(admin));
        when(tenantUserRepository.findUserIdsByTenantIdAndCreatedBy(tenantId, managerId))
                .thenReturn(List.of(onboardedUserId));

        IssuedCertificate cert = new IssuedCertificate();
        cert.setId(certificateId);
        cert.setUserId(onboardedUserId);
        cert.setStatus(CertificateStatus.FAILED);

        when(issuedRepository.findById(certificateId)).thenReturn(Optional.of(cert));
        when(tenantAccessGuard.currentTenantSlug()).thenReturn("test-tenant");

        certificateService.regenerateCertificate(certificateId);

        // Verify status reset to PENDING and save called
        assertThat(cert.getStatus()).isEqualTo(CertificateStatus.PENDING);
        verify(issuedRepository).save(cert);
    }
}

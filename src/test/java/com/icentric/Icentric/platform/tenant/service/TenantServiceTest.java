package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock TenantProvisioningService provisioningService;
    @Mock TenantUserBootstrapService bootstrapService;
    @Mock EmailService emailService;
    @Mock AuditService auditService;
    @Mock TenantUserRepository tenantUserRepository;
    @Mock UserAssignmentRepository userAssignmentRepository;
    @Mock IssuedCertificateRepository issuedCertificateRepository;
    @Mock TrackRepository trackRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock EntityManager entityManager;

    @InjectMocks TenantService tenantService;

    @Test
    @DisplayName("createTenant succeeds and orchestrates provisioning + bootstrap")
    void createTenant_success() {
        when(tenantRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.createTenant(
                "Acme Corp",
                "Pro",
                150,
                "owner@acme.com",
                "SecurePass1!"
        );

        assertThat(result.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(result.getPlan()).isEqualTo("Pro");
        assertThat(result.getMaxSeats()).isEqualTo(150);
        assertThat(result.getStatus()).isEqualTo("active");

        verify(provisioningService).provisionTenantSchema(any());
        verify(bootstrapService).createSuperAdmin(any(), eq("owner@acme.com"), eq("SecurePass1!"));
    }

    @Test
    @DisplayName("createTenant with duplicate slug appends suffix instead of failing")
    void createTenant_duplicateSlug() {
        Tenant existing = new Tenant("acme-corp", "Existing Corp");
        when(tenantRepository.findBySlug("acme-corp")).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.createTenant(
                "Acme Corp",
                "Starter",
                25,
                "owner@new.com",
                "SomePass1!"
        );

        // Slug should have a random suffix appended
        assertThat(result.getSlug()).startsWith("acme-corp-");
        assertThat(result.getSlug()).isNotEqualTo("acme-corp");
        verify(provisioningService).provisionTenantSchema(result.getSlug());
    }
}

package com.icentric.Icentric.platform.impersonation.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.platform.impersonation.entity.ImpersonationSession;
import com.icentric.Icentric.platform.impersonation.repository.ImpersonationSessionRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpersonationServiceTest {

    @Mock
    ImpersonationSessionRepository repository;
    @Mock
    JwtService jwtService;
    @Mock
    PlatformAdminRepository adminRepository;
    @Mock
    TenantRepository tenantRepository;
    @Mock
    TenantUserRepository tenantUserRepository;
    @Mock
    AuditService auditService;

    @InjectMocks
    ImpersonationService impersonationService;

    @Test
    @DisplayName("startSession validates tenant membership and uses target user's actual role")
    void startSession_success() {
        String adminEmail = "admin@icentric.com";
        String tenantSlug = "acme";
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(adminId);
        admin.setEmail(adminEmail);
        admin.setFullName("Platform Admin");

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug(tenantSlug);

        TenantUser membership = new TenantUser();
        membership.setUserId(targetUserId);
        membership.setTenantId(tenantId);
        membership.setRole("super_admin");

        when(adminRepository.findByEmail(adminEmail)).thenReturn(Optional.of(admin));
        when(tenantRepository.findBySlug(tenantSlug)).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenantId)).thenReturn(Optional.of(membership));
        when(repository.save(any(ImpersonationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateImpersonationToken(
                eq(adminEmail),
                eq(targetUserId),
                eq("ROLE_SUPER_ADMIN"),
                eq(tenantSlug),
                eq(adminId),
                any(UUID.class)
        )).thenReturn("impersonation-jwt");

        String token = impersonationService.startSession(adminEmail, targetUserId, tenantSlug, "Support case");

        assertThat(token).isEqualTo("impersonation-jwt");

        ArgumentCaptor<ImpersonationSession> sessionCaptor = ArgumentCaptor.forClass(ImpersonationSession.class);
        verify(repository).save(sessionCaptor.capture());
        ImpersonationSession saved = sessionCaptor.getValue();
        assertThat(saved.getPlatformAdminId()).isEqualTo(adminId);
        assertThat(saved.getImpersonatedUserId()).isEqualTo(targetUserId);
        assertThat(saved.getTenantSlug()).isEqualTo(tenantSlug);

        verify(auditService).logForTenant(
                eq(adminId),
                eq(AuditAction.IMPERSONATION_STARTED),
                eq("IMPERSONATION_SESSION"),
                eq(saved.getId().toString()),
                any(String.class),
                eq("system")
        );
    }

    @Test
    @DisplayName("startSession rejects target users outside tenant slug")
    void startSession_rejectsCrossTenantUser() {
        String adminEmail = "admin@icentric.com";
        String tenantSlug = "acme";
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(adminId);
        admin.setEmail(adminEmail);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug(tenantSlug);

        when(adminRepository.findByEmail(adminEmail)).thenReturn(Optional.of(admin));
        when(tenantRepository.findBySlug(tenantSlug)).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> impersonationService.startSession(adminEmail, targetUserId, tenantSlug, "Support case"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Target user not found in tenant");

        verify(repository, never()).save(any(ImpersonationSession.class));
    }
}


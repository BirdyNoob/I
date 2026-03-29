package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
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

    @Mock
    TenantRepository tenantRepository;
    @Mock
    TenantProvisioningService provisioningService;
    @Mock
    TenantUserBootstrapService bootstrapService;

    @InjectMocks
    TenantService tenantService;

    @Test
    @DisplayName("createTenant succeeds and orchestrates provisioning + bootstrap")
    void createTenant_success() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.createTenant("acme", "Acme Corp", "owner@acme.com", "SecurePass1!");

        assertThat(result.getSlug()).isEqualTo("acme");
        assertThat(result.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(result.getStatus()).isEqualTo("active");

        verify(provisioningService).provisionTenantSchema("acme");
        verify(bootstrapService).createSuperAdmin("acme", "owner@acme.com", "SecurePass1!");
    }

    @Test
    @DisplayName("createTenant rejects duplicate slug → 409")
    void createTenant_duplicateSlug() {
        Tenant existing = new Tenant("acme", "Existing Corp");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> tenantService.createTenant("acme", "New Corp", "owner@new.com", "SomePass1!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acme");

        verify(tenantRepository, never()).save(any());
        verify(provisioningService, never()).provisionTenantSchema(any());
    }
}

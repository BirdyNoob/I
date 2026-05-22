package com.icentric.Icentric.audit.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.entity.AuditLog;
import com.icentric.Icentric.audit.repository.AuditLogRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceScopingTest {

    @Mock private AuditLogRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private CertificateRepository certificateRepository;

    @InjectMocks
    private AuditService auditService;

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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void setSecurityContext(UUID userId) {
        Authentication auth = mock(Authentication.class);
        when(auth.getDetails()).thenReturn(userId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("ROLE_SUPER_ADMIN retains unrestricted global access to audit logs")
    void superAdminUnrestrictedAccess() {
        setSecurityContext(managerId);

        TenantUser superAdmin = new TenantUser();
        superAdmin.setUserId(managerId);
        superAdmin.setRole("SUPER_ADMIN");

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(superAdmin));

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setUserId(managerId);
        log.setTenantSlug("test-tenant");

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        auditService.getLogs(PageRequest.of(0, 10), null, null, null, null, null);

        // Verify that standard manager query method findUserIdsByTenantIdAndCreatedBy was NOT called for SUPER_ADMIN
        verify(tenantUserRepository, never()).findUserIdsByTenantIdAndCreatedBy(any(), any());
    }

    @Test
    @DisplayName("ROLE_ADMIN (standard manager) is scoped to view only their own logs or onboarded users' logs")
    void standardAdminScopedAccess() {
        setSecurityContext(managerId);

        TenantUser admin = new TenantUser();
        admin.setUserId(managerId);
        admin.setRole("ADMIN");

        UUID onboardedUserId = UUID.randomUUID();

        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.findByUserIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(admin));
        when(tenantUserRepository.findUserIdsByTenantIdAndCreatedBy(tenantId, managerId))
                .thenReturn(List.of(onboardedUserId));

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setUserId(managerId);
        log.setTenantSlug("test-tenant");

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        auditService.getLogs(PageRequest.of(0, 10), null, null, null, null, null);

        // Capture Specification and verify predicate calls
        ArgumentCaptor<Specification<AuditLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));

        Specification<AuditLog> spec = specCaptor.getValue();
        assertThat(spec).isNotNull();

        // Verify JPA criteria builder predicates are generated
        Root<AuditLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Path<Object> userIdPath = mock(Path.class);
        Path<Object> entityTypePath = mock(Path.class);
        Path<Object> entityIdPath = mock(Path.class);
        Path<Object> tenantSlugPath = mock(Path.class);

        when(root.get("userId")).thenReturn(userIdPath);
        when(root.get("entityType")).thenReturn(entityTypePath);
        when(root.get("entityId")).thenReturn(entityIdPath);
        when(root.get("tenantSlug")).thenReturn(tenantSlugPath);

        Expression<String> upperExpression = mock(Expression.class);
        when(cb.upper(any())).thenReturn(upperExpression);

        Predicate inUserIdsPredicate = mock(Predicate.class);
        when(userIdPath.in(any(List.class))).thenReturn(inUserIdsPredicate);

        Predicate inEntityIdsPredicate = mock(Predicate.class);
        when(entityIdPath.in(any(List.class))).thenReturn(inEntityIdsPredicate);

        Predicate equalEntityTypePredicate = mock(Predicate.class);
        when(cb.equal(any(), anyString())).thenReturn(equalEntityTypePredicate);

        Predicate targetAndPredicate = mock(Predicate.class);
        when(cb.and(equalEntityTypePredicate, inEntityIdsPredicate)).thenReturn(targetAndPredicate);

        Predicate orPredicate = mock(Predicate.class);
        when(cb.or(inUserIdsPredicate, targetAndPredicate)).thenReturn(orPredicate);

        Predicate tenantEqualPredicate = mock(Predicate.class);
        when(cb.equal(tenantSlugPath, "test-tenant")).thenReturn(tenantEqualPredicate);

        spec.toPredicate(root, query, cb);

        verify(cb).or(inUserIdsPredicate, targetAndPredicate);
        verify(cb).equal(tenantSlugPath, "test-tenant");
    }
}

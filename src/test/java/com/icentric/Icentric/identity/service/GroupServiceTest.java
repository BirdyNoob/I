package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.dto.CreateGroupRequest;
import com.icentric.Icentric.identity.dto.GroupMemberResponse;
import com.icentric.Icentric.identity.dto.GroupResponse;
import com.icentric.Icentric.identity.dto.UpdateGroupRequest;
import com.icentric.Icentric.identity.entity.GroupMembership;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.entity.UserGroup;
import com.icentric.Icentric.identity.repository.GroupMembershipRepository;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserGroupRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock private UserGroupRepository userGroupRepository;
    @Mock private GroupMembershipRepository groupMembershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private TenantAccessGuard tenantAccessGuard;
    @Mock private AuditService auditService;
    @Mock private AuditMetadataService auditMetadataService;

    private GroupService groupService;
    private Tenant tenant;

    @BeforeEach
    void setup() {
        groupService = new GroupService(
                userGroupRepository,
                groupMembershipRepository,
                userRepository,
                tenantUserRepository,
                tenantAccessGuard,
                auditService,
                auditMetadataService
        );
        tenant = new Tenant("acme", "Acme Corp");
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
    }

    private void setupMockSecurityContext(UUID actorId) {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getDetails()).thenReturn(actorId.toString());
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("createGroup saves group successfully")
    void createGroup_savesSuccessfully() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        CreateGroupRequest request = new CreateGroupRequest("Engineering", "Tech Team");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(userGroupRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), "Engineering")).thenReturn(false);

        UserGroup savedGroup = new UserGroup();
        savedGroup.setId(UUID.randomUUID());
        savedGroup.setName("Engineering");
        savedGroup.setDescription("Tech Team");
        savedGroup.setCreatedBy(actorId);
        savedGroup.setCreatedAt(Instant.now());
        when(userGroupRepository.save(any(UserGroup.class))).thenReturn(savedGroup);

        GroupResponse response = groupService.createGroup(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Engineering");
        verify(userGroupRepository).save(any(UserGroup.class));
    }

    @Test
    @DisplayName("createGroup throws error if group already exists")
    void createGroup_throwsIfAlreadyExists() {
        CreateGroupRequest request = new CreateGroupRequest("Engineering", "Tech Team");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(userGroupRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), "Engineering")).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Group already exists");
    }

    @Test
    @DisplayName("listGroups as Super Admin lists all groups")
    void listGroups_asSuperAdmin_returnsAllGroups() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        when(tenantAccessGuard.currentTenantId()).thenReturn(tenant.getId());

        UserGroup g1 = new UserGroup();
        g1.setId(UUID.randomUUID());
        g1.setCreatedBy(UUID.randomUUID());
        UserGroup g2 = new UserGroup();
        g2.setId(UUID.randomUUID());
        g2.setCreatedBy(actorId);

        when(userGroupRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()))
                .thenReturn(List.of(g1, g2));

        // Actor is Super Admin (so actorMembership is not found or has different role, e.g., actorMembership = null or role = null)
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.empty());

        List<Object[]> counts = new java.util.ArrayList<>();
        counts.add(new Object[]{g1.getId(), 5L});
        counts.add(new Object[]{g2.getId(), 10L});
        when(groupMembershipRepository.countByGroupIds(any()))
                .thenReturn(counts);

        List<GroupResponse> result = groupService.listGroups();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).memberCount()).isEqualTo(5L);
        assertThat(result.get(1).memberCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("listGroups as Standard Manager (ADMIN) filters groups by creator")
    void listGroups_asStandardManager_filtersByCreator() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        when(tenantAccessGuard.currentTenantId()).thenReturn(tenant.getId());

        UserGroup g1 = new UserGroup();
        g1.setId(UUID.randomUUID());
        g1.setCreatedBy(UUID.randomUUID()); // created by another admin

        UserGroup g2 = new UserGroup();
        g2.setId(UUID.randomUUID());
        g2.setCreatedBy(actorId); // created by this manager

        when(userGroupRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()))
                .thenReturn(List.of(g1, g2));

        TenantUser actorMembership = new TenantUser(actorId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.of(actorMembership));

        List<Object[]> counts = new java.util.ArrayList<>();
        counts.add(new Object[]{g2.getId(), 10L});
        when(groupMembershipRepository.countByGroupIds(any()))
                .thenReturn(counts);

        List<GroupResponse> result = groupService.listGroups();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memberCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("updateGroup as Super Admin succeeds for any group")
    void updateGroup_asSuperAdmin_succeeds() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        UUID groupId = UUID.randomUUID();
        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setName("Old Name");
        group.setCreatedBy(UUID.randomUUID());

        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.empty()); // Super Admin

        when(userGroupRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), "New Name")).thenReturn(false);

        UserGroup updatedGroup = new UserGroup();
        updatedGroup.setId(groupId);
        updatedGroup.setName("New Name");
        when(userGroupRepository.save(any(UserGroup.class))).thenReturn(updatedGroup);

        GroupResponse response = groupService.updateGroup(groupId, new UpdateGroupRequest("New Name", "New Description"));

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updateGroup as Standard Manager (ADMIN) on own group succeeds")
    void updateGroup_asStandardManager_ownGroup_succeeds() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        UUID groupId = UUID.randomUUID();
        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setName("Old Name");
        group.setCreatedBy(actorId); // created by actor

        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser actorMembership = new TenantUser(actorId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.of(actorMembership));

        when(userGroupRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), "New Name")).thenReturn(false);

        UserGroup updatedGroup = new UserGroup();
        updatedGroup.setId(groupId);
        updatedGroup.setName("New Name");
        when(userGroupRepository.save(any(UserGroup.class))).thenReturn(updatedGroup);

        GroupResponse response = groupService.updateGroup(groupId, new UpdateGroupRequest("New Name", "New Description"));

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updateGroup as Standard Manager (ADMIN) on other group throws AccessDeniedException")
    void updateGroup_asStandardManager_otherGroup_throwsAccessDenied() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        UUID groupId = UUID.randomUUID();
        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setName("Old Name");
        group.setCreatedBy(UUID.randomUUID()); // created by another admin

        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser actorMembership = new TenantUser(actorId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.of(actorMembership));

        assertThatThrownBy(() -> groupService.updateGroup(groupId, new UpdateGroupRequest("New Name", "New Description")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to access this group");
    }

    @Test
    @DisplayName("addMember as Standard Manager (ADMIN) on own group with onboarded user succeeds")
    void addMember_asStandardManager_ownGroup_onboardedUser_succeeds() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setCreatedBy(actorId);

        when(tenantAccessGuard.currentTenantId()).thenReturn(tenant.getId());
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser actorMembership = new TenantUser(actorId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.of(actorMembership));

        TenantUser targetUserMembership = new TenantUser(userId, tenant.getId(), "LEARNER");
        targetUserMembership.setCreatedBy(actorId); // onboarded by actor
        when(tenantUserRepository.findByUserIdAndTenantId(userId, tenant.getId()))
                .thenReturn(Optional.of(targetUserMembership));

        when(groupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

        groupService.addMember(groupId, userId);

        verify(groupMembershipRepository).save(any(GroupMembership.class));
    }

    @Test
    @DisplayName("addMember as Standard Manager (ADMIN) on own group with non-onboarded user throws AccessDeniedException")
    void addMember_asStandardManager_ownGroup_nonOnboardedUser_throwsAccessDenied() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setCreatedBy(actorId);

        when(tenantAccessGuard.currentTenantId()).thenReturn(tenant.getId());
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser actorMembership = new TenantUser(actorId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.of(actorMembership));

        TenantUser targetUserMembership = new TenantUser(userId, tenant.getId(), "LEARNER");
        targetUserMembership.setCreatedBy(UUID.randomUUID()); // onboarded by someone else
        when(tenantUserRepository.findByUserIdAndTenantId(userId, tenant.getId()))
                .thenReturn(Optional.of(targetUserMembership));

        assertThatThrownBy(() -> groupService.addMember(groupId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only add users you onboarded to groups");
    }

    @Test
    @DisplayName("removeMember as Standard Manager on own group succeeds")
    void removeMember_asStandardManager_ownGroup_succeeds() {
        UUID actorId = UUID.randomUUID();
        setupMockSecurityContext(actorId);

        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setCreatedBy(actorId);

        when(tenantAccessGuard.currentTenantId()).thenReturn(tenant.getId());
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser actorMembership = new TenantUser(actorId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()))
                .thenReturn(Optional.of(actorMembership));

        when(groupMembershipRepository.deleteByGroupIdAndUserId(groupId, userId)).thenReturn(1L);

        groupService.removeMember(groupId, userId);

        verify(groupMembershipRepository).deleteByGroupIdAndUserId(groupId, userId);
    }
}

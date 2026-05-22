package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.identity.entity.UserGroup;
import com.icentric.Icentric.identity.entity.GroupMembership;
import com.icentric.Icentric.identity.repository.UserGroupRepository;
import com.icentric.Icentric.identity.repository.GroupMembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    UserAssignmentRepository repository;
    @Mock
    TrackRepository trackRepository;
    @Mock
    AuditService auditService;
    @Mock
    UserRepository userRepository;
    @Mock
    TenantUserRepository tenantUserRepository;
    @Mock
    TenantRepository tenantRepository;
    @Mock
    TenantSchemaService tenantSchemaService;
    @Mock
    com.icentric.Icentric.content.repository.LessonRepository lessonRepository;
    @Mock
    com.icentric.Icentric.learning.repository.LessonProgressRepository lessonProgressRepository;
    @Mock
    com.icentric.Icentric.audit.service.AuditMetadataService auditMetadataService;
    @Mock
    com.icentric.Icentric.identity.service.TenantAccessGuard tenantAccessGuard;
    @Mock
    com.icentric.Icentric.common.mail.EmailService emailService;
    @Mock
    UserGroupRepository userGroupRepository;
    @Mock
    GroupMembershipRepository groupMembershipRepository;

    @InjectMocks
    AssignmentService assignmentService;

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
    @DisplayName("bulkAssign applies tenant schema before repository lookup")
    void bulkAssign_appliesTenantSchemaBeforeAssignmentLookup() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Track track = new Track();
        track.setId(trackId);
        track.setVersion(3);

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");

        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);
        when(userRepository.findByIdIn(List.of(userId))).thenReturn(List.of(user));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(repository.findByUserIdAndTrackId(userId, trackId)).thenReturn(Optional.empty());
        when(repository.save(any(UserAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assignmentService.bulkAssign(new BulkAssignmentRequest(
                trackId,
                List.of(userId),
                null,
                Instant.now().plusSeconds(3600)
        ));

        InOrder inOrder = inOrder(tenantSchemaService, repository);
        inOrder.verify(tenantSchemaService).applyCurrentTenantSearchPath();
        inOrder.verify(repository).findByUserIdAndTrackId(userId, trackId);

        verifyNoMoreInteractions(auditService);
    }

    @Test
    @DisplayName("assignTrack by ADMIN on non-onboarded user throws AccessDeniedException")
    void assignTrack_byAdmin_onNonOnboardedUser_throwsAccessDenied() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID targetUserId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        TenantUser targetMembership = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMembership.setCreatedBy(UUID.randomUUID()); // owned by another admin
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId()))
                .thenReturn(Optional.of(targetMembership));

        CreateAssignmentRequest request = new CreateAssignmentRequest(targetUserId, trackId, null);

        assertThatThrownBy(() -> assignmentService.assignTrack(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to assign tracks to this user");
    }

    @Test
    @DisplayName("assignTrack by ADMIN on onboarded user succeeds")
    void assignTrack_byAdmin_onOnboardedUser_succeeds() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID targetUserId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        TenantUser targetMembership = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMembership.setCreatedBy(adminId); // owned by this admin
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId()))
                .thenReturn(Optional.of(targetMembership));

        Track track = new Track();
        track.setId(trackId);
        track.setTitle("Cybersecurity Fundamentals");
        track.setVersion(1);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(repository.findByUserIdAndTrackId(targetUserId, trackId)).thenReturn(Optional.empty());
        when(repository.save(any(UserAssignment.class))).thenAnswer(inv -> inv.getArgument(0, UserAssignment.class));

        CreateAssignmentRequest request = new CreateAssignmentRequest(targetUserId, trackId, null);

        UserAssignment result = assignmentService.assignTrack(request);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(targetUserId);
        assertThat(result.getTrackId()).isEqualTo(trackId);
    }

    @Test
    @DisplayName("bulkAssign by ADMIN with non-onboarded user in list throws AccessDeniedException")
    void bulkAssign_byAdmin_withNonOnboardedUser_throwsAccessDenied() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID targetUserId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        TenantUser targetMembership = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMembership.setCreatedBy(UUID.randomUUID()); // owned by another admin
        when(tenantUserRepository.findByTenantIdAndUserIdIn(tenant.getId(), new HashSet<>(List.of(targetUserId))))
                .thenReturn(List.of(targetMembership));

        BulkAssignmentRequest request = new BulkAssignmentRequest(trackId, List.of(targetUserId), null, null);

        assertThatThrownBy(() -> assignmentService.bulkAssign(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to assign tracks to one or more of these users");
    }

    @Test
    @DisplayName("bulkAssign by ADMIN with department filter scopes to only onboarded users")
    void bulkAssign_byAdmin_withDepartmentFilter_scopesToOnboarded() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID trackId = UUID.randomUUID();
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        TenantUser mapping1 = new TenantUser(user1Id, tenant.getId(), "LEARNER");
        mapping1.setDepartment(Department.ENGINEERING);
        mapping1.setCreatedBy(adminId); // owned by admin

        TenantUser mapping2 = new TenantUser(user2Id, tenant.getId(), "LEARNER");
        mapping2.setDepartment(Department.ENGINEERING);
        mapping2.setCreatedBy(UUID.randomUUID()); // owned by someone else

        when(tenantUserRepository.findByTenantId(tenant.getId())).thenReturn(List.of(mapping1, mapping2));

        User user1 = new User();
        user1.setId(user1Id);
        when(userRepository.findByIdIn(List.of(user1Id))).thenReturn(List.of(user1));

        Track track = new Track();
        track.setId(trackId);
        track.setVersion(1);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(repository.findByUserIdAndTrackId(user1Id, trackId)).thenReturn(Optional.empty());
        when(repository.save(any(UserAssignment.class))).thenAnswer(inv -> inv.getArgument(0, UserAssignment.class));

        BulkAssignmentRequest request = new BulkAssignmentRequest(trackId, null, Department.ENGINEERING, null);

        var result = assignmentService.bulkAssign(request);

        assertThat(result).isNotNull();
        verify(repository).findByUserIdAndTrackId(user1Id, trackId);
    }

    @Test
    @DisplayName("searchAssignments by ADMIN scopes search results using createdBy filter")
    void searchAssignments_byAdmin_passesCreatedByFilter() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        Pageable pageable = PageRequest.of(0, 10);
        Page<UserAssignment> expectedPage = new PageImpl<>(List.of());
        when(repository.searchAssignments(tenant.getId(), null, null, null, adminId, pageable))
                .thenReturn(expectedPage);

        var result = assignmentService.searchAssignments(null, null, null, pageable);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("searchAssignments by ADMIN for specific non-onboarded user throws AccessDeniedException")
    void searchAssignments_byAdmin_forNonOnboardedUser_throwsAccessDenied() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID targetUserId = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        TenantUser targetMembership = new TenantUser(targetUserId, tenant.getId(), "LEARNER");
        targetMembership.setCreatedBy(UUID.randomUUID()); // owned by another admin
        when(tenantUserRepository.findByUserIdAndTenantId(targetUserId, tenant.getId()))
                .thenReturn(Optional.of(targetMembership));

        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> assignmentService.searchAssignments(null, null, targetUserId, pageable))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to view this user's assignments");
    }

    @Test
    @DisplayName("bulkAssign by SUPER_ADMIN for group assigns to all members")
    void bulkAssign_withGroup_asSuperAdmin_assignsToAllGroupMembers() {
        UUID trackId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setTenantId(tenant.getId());
        group.setName("Engineering");
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        GroupMembership gm1 = new GroupMembership();
        gm1.setGroupId(groupId);
        gm1.setUserId(user1Id);

        GroupMembership gm2 = new GroupMembership();
        gm2.setGroupId(groupId);
        gm2.setUserId(user2Id);

        when(groupMembershipRepository.findByGroupIdOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(gm1, gm2));

        User user1 = new User();
        user1.setId(user1Id);
        user1.setEmail("u1@icentric.com");
        User user2 = new User();
        user2.setId(user2Id);
        user2.setEmail("u2@icentric.com");

        when(userRepository.findByIdIn(List.of(user1Id, user2Id)))
                .thenReturn(List.of(user1, user2));

        Track track = new Track();
        track.setId(trackId);
        track.setVersion(1);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        BulkAssignmentRequest request = new BulkAssignmentRequest(
                trackId,
                null,
                null,
                groupId,
                Instant.now().plusSeconds(3600)
        );

        var result = assignmentService.bulkAssign(request);
        assertThat(result.get("total")).isEqualTo(2);
        assertThat(result.get("success")).isEqualTo(2);
    }

    @Test
    @DisplayName("bulkAssign by ADMIN for group assigns only to members onboarded by that ADMIN")
    void bulkAssign_withGroup_asAdmin_assignsOnlyToOnboardedGroupMembers() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID trackId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setTenantId(tenant.getId());
        group.setName("Engineering");
        group.setCreatedBy(adminId); // created by this admin
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        GroupMembership gm1 = new GroupMembership();
        gm1.setGroupId(groupId);
        gm1.setUserId(user1Id);

        GroupMembership gm2 = new GroupMembership();
        gm2.setGroupId(groupId);
        gm2.setUserId(user2Id);

        when(groupMembershipRepository.findByGroupIdOrderByCreatedAtDesc(groupId))
                .thenReturn(List.of(gm1, gm2));

        TenantUser tu1 = new TenantUser(user1Id, tenant.getId(), "LEARNER");
        tu1.setCreatedBy(adminId); // onboarded by admin

        TenantUser tu2 = new TenantUser(user2Id, tenant.getId(), "LEARNER");
        tu2.setCreatedBy(UUID.randomUUID()); // onboarded by another admin

        when(tenantUserRepository.findByTenantIdAndUserIdIn(tenant.getId(), new HashSet<>(List.of(user1Id, user2Id))))
                .thenReturn(List.of(tu1, tu2));

        User user1 = new User();
        user1.setId(user1Id);
        user1.setEmail("u1@icentric.com");

        when(userRepository.findByIdIn(List.of(user1Id)))
                .thenReturn(List.of(user1));

        Track track = new Track();
        track.setId(trackId);
        track.setVersion(1);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(repository.save(any(UserAssignment.class))).thenAnswer(inv -> inv.getArgument(0, UserAssignment.class));

        BulkAssignmentRequest request = new BulkAssignmentRequest(
                trackId,
                null,
                null,
                groupId,
                Instant.now().plusSeconds(3600)
        );

        var result = assignmentService.bulkAssign(request);
        assertThat(result.get("total")).isEqualTo(1);
        assertThat(result.get("success")).isEqualTo(1);
    }

    @Test
    @DisplayName("bulkAssign by ADMIN for group created by another ADMIN throws AccessDeniedException")
    void bulkAssign_withGroup_asAdmin_notCreatorOfGroup_throwsAccessDenied() {
        UUID adminId = UUID.randomUUID();
        setupMockSecurityContext(adminId);

        UUID trackId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        com.icentric.Icentric.platform.tenant.entity.Tenant tenant = new com.icentric.Icentric.platform.tenant.entity.Tenant("acme", "Acme Corp");
        when(tenantAccessGuard.currentTenant()).thenReturn(tenant);

        UserGroup group = new UserGroup();
        group.setId(groupId);
        group.setTenantId(tenant.getId());
        group.setName("Engineering");
        group.setCreatedBy(UUID.randomUUID()); // created by another admin
        when(userGroupRepository.findByIdAndTenantId(groupId, tenant.getId()))
                .thenReturn(Optional.of(group));

        TenantUser adminMembership = new TenantUser(adminId, tenant.getId(), "ADMIN");
        when(tenantUserRepository.findByUserIdAndTenantId(adminId, tenant.getId()))
                .thenReturn(Optional.of(adminMembership));

        BulkAssignmentRequest request = new BulkAssignmentRequest(
                trackId,
                null,
                null,
                groupId,
                Instant.now().plusSeconds(3600)
        );

        assertThatThrownBy(() -> assignmentService.bulkAssign(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to access this group");
    }
}


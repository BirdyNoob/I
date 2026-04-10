package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.constants.AuditAction;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final UserGroupRepository userGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public GroupService(
            UserGroupRepository userGroupRepository,
            GroupMembershipRepository groupMembershipRepository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantAccessGuard tenantAccessGuard,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.userGroupRepository = userGroupRepository;
        this.groupMembershipRepository = groupMembershipRepository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantAccessGuard = tenantAccessGuard;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        Tenant tenant = tenantAccessGuard.currentTenant();
        String normalizedName = normalizeRequiredName(request.name());
        if (userGroupRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), normalizedName)) {
            throw new IllegalStateException("Group already exists: " + normalizedName);
        }

        UserGroup group = new UserGroup();
        group.setId(UUID.randomUUID());
        group.setTenantId(tenant.getId());
        group.setName(normalizedName);
        group.setDescription(normalizeOptionalText(request.description()));
        group.setCreatedAt(Instant.now());
        group.setCreatedBy(currentActorUserId());
        UserGroup saved = userGroupRepository.save(group);

        logGroupAction(AuditAction.CREATE_GROUP, saved.getId(), "created group " + saved.getName());
        return toGroupResponse(saved, 0L);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups() {
        UUID tenantId = tenantAccessGuard.currentTenantId();
        List<UserGroup> groups = userGroupRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<UUID> groupIds = groups.stream().map(UserGroup::getId).toList();
        Map<UUID, Long> countsByGroupId = new HashMap<>();
        for (Object[] row : groupMembershipRepository.countByGroupIds(groupIds)) {
            countsByGroupId.put((UUID) row[0], ((Long) row[1]));
        }

        return groups.stream()
                .map(g -> toGroupResponse(g, countsByGroupId.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request) {
        Tenant tenant = tenantAccessGuard.currentTenant();
        UserGroup group = getTenantGroup(groupId, tenant.getId());

        if (request.name() != null) {
            String normalizedName = normalizeRequiredName(request.name());
            if (!group.getName().equalsIgnoreCase(normalizedName)
                    && userGroupRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), normalizedName)) {
                throw new IllegalStateException("Group already exists: " + normalizedName);
            }
            group.setName(normalizedName);
        }
        if (request.description() != null) {
            group.setDescription(normalizeOptionalText(request.description()));
        }

        UserGroup saved = userGroupRepository.save(group);
        long count = groupMembershipRepository.countByGroupId(saved.getId());
        logGroupAction(AuditAction.UPDATE_GROUP, saved.getId(), "updated group " + saved.getName());
        return toGroupResponse(saved, count);
    }

    @Transactional
    public void deleteGroup(UUID groupId) {
        UUID tenantId = tenantAccessGuard.currentTenantId();
        UserGroup group = getTenantGroup(groupId, tenantId);
        userGroupRepository.delete(group);
        logGroupAction(AuditAction.DELETE_GROUP, groupId, "deleted group " + group.getName());
    }

    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        UUID tenantId = tenantAccessGuard.currentTenantId();
        UserGroup group = getTenantGroup(groupId, tenantId);
        tenantAccessGuard.assertUserBelongsToCurrentTenant(userId);

        if (groupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalStateException("User is already in this group");
        }

        GroupMembership membership = new GroupMembership();
        membership.setId(UUID.randomUUID());
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setCreatedAt(Instant.now());
        groupMembershipRepository.save(membership);

        logGroupAction(
                AuditAction.ADD_GROUP_MEMBER,
                groupId,
                "added " + auditMetadataService.describeUserInCurrentTenant(userId) + " to group " + group.getName()
        );
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        UUID tenantId = tenantAccessGuard.currentTenantId();
        UserGroup group = getTenantGroup(groupId, tenantId);
        long deleted = groupMembershipRepository.deleteByGroupIdAndUserId(groupId, userId);
        if (deleted == 0) {
            throw new NoSuchElementException("User is not in this group");
        }

        logGroupAction(
                AuditAction.REMOVE_GROUP_MEMBER,
                groupId,
                "removed user " + userId + " from group " + group.getName()
        );
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> listMembers(UUID groupId) {
        Tenant tenant = tenantAccessGuard.currentTenant();
        UserGroup group = getTenantGroup(groupId, tenant.getId());

        List<GroupMembership> memberships = groupMembershipRepository.findByGroupIdOrderByCreatedAtDesc(group.getId());
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = memberships.stream().map(GroupMembership::getUserId).toList();
        Map<UUID, User> usersById = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<UUID, TenantUser> tenantUsersById = tenantUserRepository.findByTenantIdAndUserIdIn(tenant.getId(), userIds).stream()
                .collect(Collectors.toMap(TenantUser::getUserId, tenantUser -> tenantUser));

        return memberships.stream()
                .map(membership -> toMemberResponse(usersById.get(membership.getUserId()), tenantUsersById.get(membership.getUserId())))
                .toList();
    }

    private UserGroup getTenantGroup(UUID groupId, UUID tenantId) {
        return userGroupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));
    }

    private GroupResponse toGroupResponse(UserGroup group, long memberCount) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                memberCount,
                group.getCreatedAt()
        );
    }

    private GroupMemberResponse toMemberResponse(User user, TenantUser tenantUser) {
        if (user == null || tenantUser == null) {
            throw new NoSuchElementException("Group member data is inconsistent");
        }
        return new GroupMemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                tenantUser.getRole(),
                tenantUser.getDepartment(),
                Boolean.TRUE.equals(user.getIsActive())
        );
    }

    private String normalizeRequiredName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Group name is required");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Group name is required");
        }
        return trimmed;
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UUID currentActorUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            return null;
        }
        return UUID.fromString(userIdRaw.toString());
    }

    private void logGroupAction(AuditAction action, UUID groupId, String details) {
        UUID actorId = currentActorUserId();
        if (actorId == null) {
            return;
        }
        auditService.log(actorId, action, "GROUP", groupId.toString(), auditMetadataService.describeUser(actorId) + " " + details);
    }
}

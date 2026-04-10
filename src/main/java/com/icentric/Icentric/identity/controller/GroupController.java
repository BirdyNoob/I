package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.AddGroupMemberRequest;
import com.icentric.Icentric.identity.dto.CreateGroupRequest;
import com.icentric.Icentric.identity.dto.GroupMemberResponse;
import com.icentric.Icentric.identity.dto.GroupResponse;
import com.icentric.Icentric.identity.dto.UpdateGroupRequest;
import com.icentric.Icentric.identity.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/groups")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
@Validated
@Tag(name = "Group Management", description = "APIs for creating tenant groups and managing group membership")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @Operation(summary = "Create group", description = "Creates a group in the current tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Group already exists")
    })
    @PostMapping
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(request);
    }

    @Operation(summary = "List groups", description = "Returns all groups in the current tenant.")
    @ApiResponse(responseCode = "200", description = "Groups returned")
    @GetMapping
    public List<GroupResponse> listGroups() {
        return groupService.listGroups();
    }

    @Operation(summary = "Update group", description = "Updates group name and/or description.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group updated"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PutMapping("/{groupId}")
    public GroupResponse updateGroup(
            @Parameter(description = "Group ID") @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request
    ) {
        return groupService.updateGroup(groupId, request);
    }

    @Operation(summary = "Delete group", description = "Deletes a group and all its memberships.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Group deleted"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@Parameter(description = "Group ID") @PathVariable UUID groupId) {
        groupService.deleteGroup(groupId);
    }

    @Operation(summary = "List group members", description = "Returns all members of a group.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members returned"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/{groupId}/members")
    public List<GroupMemberResponse> listMembers(@Parameter(description = "Group ID") @PathVariable UUID groupId) {
        return groupService.listMembers(groupId);
    }

    @Operation(summary = "Add group member", description = "Adds a tenant user to a group.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member added"),
            @ApiResponse(responseCode = "404", description = "Group or user not found"),
            @ApiResponse(responseCode = "409", description = "User already in group")
    })
    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(
            @Parameter(description = "Group ID") @PathVariable UUID groupId,
            @Valid @RequestBody AddGroupMemberRequest request
    ) {
        groupService.addMember(groupId, request.userId());
    }

    @Operation(summary = "Remove group member", description = "Removes a user from a group.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member removed"),
            @ApiResponse(responseCode = "404", description = "Group/member not found")
    })
    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @Parameter(description = "Group ID") @PathVariable UUID groupId,
            @Parameter(description = "User ID") @PathVariable UUID userId
    ) {
        groupService.removeMember(groupId, userId);
    }
}

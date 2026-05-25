package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.common.enums.Department;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BulkAssignmentRequest(

        @NotNull
        UUID trackId,
        List<@NotNull UUID> userIds,
        Department department,
        UUID groupId,
        Boolean allCompany,
        @NotNull
        @Future
        Instant dueDate

) {
    public BulkAssignmentRequest(UUID trackId, List<UUID> userIds, Department department, Instant dueDate) {
        this(trackId, userIds, department, null, false, dueDate);
    }

    public BulkAssignmentRequest(UUID trackId, List<UUID> userIds, Department department, UUID groupId, Instant dueDate) {
        this(trackId, userIds, department, groupId, false, dueDate);
    }

    @AssertTrue(message = "Provide at least one userId, a department, a groupId, or set allCompany to true")
    public boolean hasAudience() {
        boolean hasUserIds = userIds != null && !userIds.isEmpty();
        boolean hasDepartment = department != null;
        boolean hasGroupId = groupId != null;
        boolean hasAllCompany = allCompany != null && allCompany;
        return hasUserIds || hasDepartment || hasGroupId || hasAllCompany;
    }
}

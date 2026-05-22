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
        @NotNull
        @Future
        Instant dueDate

) {
    public BulkAssignmentRequest(UUID trackId, List<UUID> userIds, Department department, Instant dueDate) {
        this(trackId, userIds, department, null, dueDate);
    }

    @AssertTrue(message = "Provide at least one userId, a department, or a groupId")
    public boolean hasAudience() {
        boolean hasUserIds = userIds != null && !userIds.isEmpty();
        boolean hasDepartment = department != null;
        boolean hasGroupId = groupId != null;
        return hasUserIds || hasDepartment || hasGroupId;
    }
}

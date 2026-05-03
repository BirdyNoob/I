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
        @NotNull
        @Future
        Instant dueDate

) {
    @AssertTrue(message = "Provide at least one userId or a department")
    public boolean hasAudience() {
        boolean hasUserIds = userIds != null && !userIds.isEmpty();
        boolean hasDepartment = department != null;
        return hasUserIds || hasDepartment;
    }
}

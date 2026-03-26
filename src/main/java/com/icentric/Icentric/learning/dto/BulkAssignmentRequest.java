package com.icentric.Icentric.learning.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BulkAssignmentRequest(

        @NotNull
        UUID trackId,
        List<@NotNull UUID> userIds,
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "department must not start or end with whitespace")
        @Size(max = 100)
        String department,
        @NotNull
        @Future
        Instant dueDate

) {
    @AssertTrue(message = "Provide at least one userId or a department")
    public boolean hasAudience() {
        boolean hasUserIds = userIds != null && !userIds.isEmpty();
        boolean hasDepartment = department != null && !department.isBlank();
        return hasUserIds || hasDepartment;
    }
}

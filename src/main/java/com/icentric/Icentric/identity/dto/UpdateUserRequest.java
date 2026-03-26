package com.icentric.Icentric.identity.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Pattern(regexp = "LEARNER|ADMIN|SUPER_ADMIN", message = "role must be LEARNER, ADMIN, or SUPER_ADMIN")
        String role,
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "department must not start or end with whitespace")
        @Size(max = 100)
        String department,
        Boolean isActive
) {
        @AssertTrue(message = "Provide at least one field to update")
        public boolean hasUpdates() {
                return role != null || department != null || isActive != null;
        }
}

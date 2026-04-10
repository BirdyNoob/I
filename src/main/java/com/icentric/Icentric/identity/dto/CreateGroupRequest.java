package com.icentric.Icentric.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "name must not start or end with whitespace")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description
) {
}

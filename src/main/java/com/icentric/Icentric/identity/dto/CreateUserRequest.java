package com.icentric.Icentric.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "name must not start or end with whitespace")
        @Size(max = 100)
        String name,
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
        @NotBlank
        @Size(min = 8, max = 128)
        String password,
        @NotBlank
        @Pattern(regexp = "LEARNER|ADMIN|SUPER_ADMIN", message = "role must be LEARNER, ADMIN, or SUPER_ADMIN")
        String role,
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "department must not start or end with whitespace")
        @Size(max = 100)
        String department,
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "location must not start or end with whitespace")
        @Size(max = 100)
        String location

) {}

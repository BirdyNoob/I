package com.icentric.Icentric.platform.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateTenantRequest(
                @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase letters, digits, or hyphens") String slug,

                @NotBlank String companyName,

                @Email @NotBlank String adminEmail) {
}

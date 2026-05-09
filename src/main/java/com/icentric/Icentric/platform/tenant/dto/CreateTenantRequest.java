package com.icentric.Icentric.platform.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
                @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase letters, digits, or hyphens") String slug,

                @NotBlank String companyName,

                @NotBlank String plan,

                @NotNull @Min(value = 1, message = "maxSeats must be at least 1") Integer maxSeats,

                @Email @NotBlank String adminEmail,

                @NotBlank @Size(min = 8, max = 128) String adminPassword) {
}

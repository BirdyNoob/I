package com.icentric.Icentric.platform.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PlatformLoginRequest(
                @Email @NotBlank String email,
                @NotBlank String password,
                String mfaCode // Optional: only required when MFA is enrolled
) {
}
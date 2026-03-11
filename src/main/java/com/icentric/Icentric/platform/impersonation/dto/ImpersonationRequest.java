package com.icentric.Icentric.platform.impersonation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ImpersonationRequest(
                @NotNull UUID targetUserId,
                @NotBlank String reason) {
}
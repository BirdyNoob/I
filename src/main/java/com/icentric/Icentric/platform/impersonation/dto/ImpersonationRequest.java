package com.icentric.Icentric.platform.impersonation.dto;
import java.util.UUID;

public record ImpersonationRequest(
        UUID targetUserId,
        String reason
) {}
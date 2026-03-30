package com.icentric.Icentric.audit.dto;

import com.icentric.Icentric.audit.constants.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        AuditAction action,
        String actionLabel,
        String entityType,
        String entityId,
        String entityDisplayName,
        String summary,
        Instant createdAt,
        UUID actorUserId,
        String actorName,
        String actorEmail,
        String actorDepartment,
        String actorRole,
        String tenantSlug,
        String tenantName
) {
}

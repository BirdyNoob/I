package com.icentric.Icentric.audit.dto;

public record AuditLogsEmailResponse(
        boolean success,
        String message,
        String recipientEmail
) {
}

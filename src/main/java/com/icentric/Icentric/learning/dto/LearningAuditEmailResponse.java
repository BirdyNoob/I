package com.icentric.Icentric.learning.dto;

/**
 * Structured JSON response returned when an asynchronous learning audit report email is successfully queued.
 */
public record LearningAuditEmailResponse(
        boolean success,
        String message,
        String recipientEmail
) {}

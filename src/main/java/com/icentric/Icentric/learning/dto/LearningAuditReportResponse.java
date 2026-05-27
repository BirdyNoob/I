package com.icentric.Icentric.learning.dto;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record LearningAuditReportResponse(
        List<EmployeeAuditRow> employees,
        int totalElements,
        int totalPages
) {
    public record EmployeeAuditRow(
            UUID userId,
            String name,
            String email,
            String department,
            ComplianceStatus complianceStatus,
            ExcellenceMetrics excellenceMetrics,
            List<CertificateSummary> certificatesEarned
    ) {}

    public record ComplianceStatus(
            long totalAssigned,
            long completed,
            long overdue,
            double progressPercent
    ) {}

    public record ExcellenceMetrics(
            double learningScore,
            double averageQuizScorePercent,
            double firstTimePassRatePercent,
            double averageDaysToComplete,
            String talentCategory
    ) {}

    public record CertificateSummary(
            UUID certificateId,
            String trackTitle,
            Instant issuedAt
    ) {}
}

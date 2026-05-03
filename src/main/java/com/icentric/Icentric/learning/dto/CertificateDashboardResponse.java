package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.common.enums.Department;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for GET /api/v1/learner/certificates/dashboard
 * Matches the agreed frontend contract exactly.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateDashboardResponse {

    private LearnerInfo learnerInfo;
    private Summary summary;
    private List<EarnedCertificate> earnedCertificates;
    private List<InProgressCertificate> inProgressCertificates;

    // ── Nested types ──────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class LearnerInfo {
        private String fullName;
        private Department department;
        private String avatarInitials;
    }

    @Data
    @Builder
    public static class Summary {
        private int earnedCount;
        private int totalAssigned;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EarnedCertificate {
        private String certificateId;       // full UUID
        private String displayId;           // human-readable verify token
        private String trackName;
        private String certificateTitle;
        private Integer score;              // 0-100, null if not stored
        private String issuedDate;          // formatted: "March 4, 2026"
        private String contentVersion;      // "v3.2"
        private String verifyUrl;
        private ShareLinks shareLinks;
    }

    @Data
    @Builder
    public static class ShareLinks {
        private String pdfDownload;
        private String linkedIn;
        private String copyLink;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InProgressCertificate {
        private String certificateId;
        private String displayId;
        private String trackName;
        private Progress progress;
        private String unlockRequirement;
        private String actionUrl;
    }

    @Data
    @Builder
    public static class Progress {
        private int modulesCompleted;
        private int totalModules;
    }
}

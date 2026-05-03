package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.CertificateStatus;
import com.icentric.Icentric.learning.dto.CertificateDashboardResponse;
import com.icentric.Icentric.learning.entity.Certificate;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.AssessmentAttemptRepository;
import com.icentric.Icentric.learning.repository.AssessmentConfigRepository;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CertificateDashboardService {

    private static final DateTimeFormatter ISSUED_DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final CertificateRepository certificateRepository;
    private final TrackRepository trackRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentConfigRepository assessmentConfigRepository;
    private final CertificateUrlService certificateUrlService;
    private final TenantSchemaService tenantSchemaService;

    public CertificateDashboardService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            UserAssignmentRepository assignmentRepository,
            IssuedCertificateRepository issuedCertificateRepository,
            CertificateRepository certificateRepository,
            TrackRepository trackRepository,
            ModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository progressRepository,
            AssessmentAttemptRepository assessmentAttemptRepository,
            AssessmentConfigRepository assessmentConfigRepository,
            CertificateUrlService certificateUrlService,
            TenantSchemaService tenantSchemaService
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.assignmentRepository = assignmentRepository;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.certificateRepository = certificateRepository;
        this.trackRepository = trackRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.assessmentConfigRepository = assessmentConfigRepository;
        this.certificateUrlService = certificateUrlService;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Transactional(readOnly = true)
    public CertificateDashboardResponse getDashboard(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        // ── Learner info ──────────────────────────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found: " + userId));

        String tenantSlug = TenantContext.getTenant();
        Department department = tenantUserRepository.findByUserId(userId)
                .stream()
                .findFirst()
                .map(TenantUser::getDepartment)
                .orElse(null);

        String fullName = (user.getName() != null && !user.getName().isBlank())
                ? user.getName() : user.getEmail();
        String avatarInitials = buildInitials(fullName);

        CertificateDashboardResponse.LearnerInfo learnerInfo = CertificateDashboardResponse.LearnerInfo.builder()
                .fullName(fullName)
                .department(department)
                .avatarInitials(avatarInitials)
                .build();

        // ── Assigned tracks ───────────────────────────────────────────────────
        List<UserAssignment> assignments = assignmentRepository.findByUserId(userId);
        int totalAssigned = assignments.size();

        // ── Earned certificates (ISSUED or PENDING status) ────────────────────
        List<IssuedCertificate> issued = issuedCertificateRepository.findByUserId(userId);
        List<CertificateDashboardResponse.EarnedCertificate> earned = new ArrayList<>();

        for (IssuedCertificate ic : issued) {
            // Only surface certificates that have been fully issued
            if (ic.getStatus() == null || ic.getStatus() == CertificateStatus.FAILED) {
                continue;
            }

            Track track = trackRepository.findById(ic.getTrackId()).orElse(null);
            String trackName = track != null ? track.getTitle() : "Unknown Track";
            String contentVersion = track != null && track.getVersion() != null
                    ? "v" + track.getVersion() : null;

            Certificate cert = ic.getCertificateId() != null
                    ? certificateRepository.findById(ic.getCertificateId()).orElse(null)
                    : null;
            String certTitle = cert != null ? cert.getTitle() : "Certificate of Completion";

            // Best PASSED assessment score for THIS specific track
            String trackIdStr = ic.getTrackId().toString();
            List<String> configIds = assessmentConfigRepository.findByTrackId(trackIdStr)
                    .stream()
                    .map(com.icentric.Icentric.learning.entity.AssessmentConfig::getId)
                    .toList();

            Integer bestScore = null;
            if (!configIds.isEmpty()) {
                bestScore = assessmentAttemptRepository
                        .findByUserIdAndStatus(userId, "PASSED")
                        .stream()
                        .filter(a -> configIds.contains(a.getAssessmentConfigId()) && a.getScore() != null)
                        .map(a -> a.getScore())
                        .max(Integer::compareTo)
                        .orElse(null);
            }

            String issuedDate = ic.getIssuedAt() != null
                    ? ISSUED_DATE_FMT.format(ic.getIssuedAt()) : null;

            // Build verify + download URLs using the real token
            String verifyUrl = ic.getVerificationToken() != null
                    ? certificateUrlService.verificationUrl(ic.getId(), ic.getVerificationToken())
                    : null;
            String downloadUrl = ic.getVerificationToken() != null
                    ? certificateUrlService.downloadUrl(ic.getId(), ic.getVerificationToken())
                    : ic.getDownloadUrl();

            // LinkedIn share pre-fill URL
            String linkedInUrl = verifyUrl != null
                    ? "https://www.linkedin.com/sharing/share-offsite/?url=" + java.net.URLEncoder.encode(verifyUrl, java.nio.charset.StandardCharsets.UTF_8)
                    : null;

            // Use verification token as the human-readable certificate ID (short UUID)
            String certDisplayId = ic.getVerificationToken() != null
                    ? ic.getVerificationToken().toString().toUpperCase().replace("-", "").substring(0, 16)
                    : ic.getId().toString();

            earned.add(CertificateDashboardResponse.EarnedCertificate.builder()
                    .certificateId(ic.getId().toString())
                    .displayId(certDisplayId)
                    .trackName(trackName)
                    .certificateTitle(certTitle)
                    .score(bestScore)
                    .issuedDate(issuedDate)
                    .contentVersion(contentVersion)
                    .verifyUrl(verifyUrl)
                    .shareLinks(CertificateDashboardResponse.ShareLinks.builder()
                            .pdfDownload(downloadUrl)
                            .linkedIn(linkedInUrl)
                            .copyLink(verifyUrl)
                            .build())
                    .build());
        }

        // Sort earned: most recently issued first — UI shows index[0] as the primary card
        // Build a fast lookup: certDisplayId -> issuedAt epoch
        java.util.Map<String, Long> issuedAtByDisplayId = new java.util.HashMap<>();
        for (IssuedCertificate ic : issued) {
            if (ic.getStatus() == null || ic.getStatus() == CertificateStatus.FAILED) continue;
            String displayId = ic.getVerificationToken() != null
                    ? ic.getVerificationToken().toString().toUpperCase().replace("-", "").substring(0, 16)
                    : ic.getId().toString();
            issuedAtByDisplayId.put(displayId, ic.getIssuedAt() != null ? ic.getIssuedAt().getEpochSecond() : 0L);
        }
        earned.sort(Comparator.comparingLong(
                (CertificateDashboardResponse.EarnedCertificate e) ->
                        issuedAtByDisplayId.getOrDefault(e.getCertificateId(), 0L)
        ).reversed());

        // ── In-progress certificates (assigned but not yet earned) ─────────────
        List<CertificateDashboardResponse.InProgressCertificate> inProgress = new ArrayList<>();

        for (UserAssignment assignment : assignments) {
            UUID trackId = assignment.getTrackId();
            boolean alreadyEarned = issued.stream()
                    .anyMatch(ic -> trackId.equals(ic.getTrackId())
                            && ic.getStatus() != null
                            && ic.getStatus() != CertificateStatus.FAILED);
            if (alreadyEarned) continue;

            Track track = trackRepository.findById(trackId).orElse(null);
            if (track == null) continue;

            List<CourseModule> modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);
            int totalModules = modules.size();
            int completedModules = 0;
            for (CourseModule module : modules) {
                var lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());
                boolean allDone = !lessons.isEmpty() && lessons.stream().allMatch(l ->
                        progressRepository.existsByUserIdAndLessonIdAndStatus(userId, l.getId(), "COMPLETED"));
                if (allDone) completedModules++;
            }

            // Check if there is a passed assessment for this track; if so skip (cert pending)
            String trackIdStr = trackId.toString();
            List<String> configIds = assessmentConfigRepository.findByTrackId(trackIdStr)
                    .stream()
                    .map(com.icentric.Icentric.learning.entity.AssessmentConfig::getId)
                    .toList();

            boolean hasPassed = false;
            String pendingCertId = null;
            if (!configIds.isEmpty()) {
                var passedAttempt = assessmentAttemptRepository
                        .findByUserIdAndStatus(userId, "PASSED")
                        .stream()
                        .filter(a -> configIds.contains(a.getAssessmentConfigId()))
                        .findFirst();
                
                if (passedAttempt.isPresent()) {
                    hasPassed = true;
                    pendingCertId = passedAttempt.get().getCertificateId();
                }
            }

            // Find matching issued certificate record (e.g. FAILED status)
            IssuedCertificate matchingCert = issued.stream()
                    .filter(ic -> trackId.equals(ic.getTrackId()))
                    .findFirst()
                    .orElse(null);

            String currentCertId = matchingCert != null ? matchingCert.getId().toString() : pendingCertId;
            String currentDisplayId = null;
            if (matchingCert != null) {
                currentDisplayId = matchingCert.getVerificationToken() != null
                        ? matchingCert.getVerificationToken().toString().toUpperCase().replace("-", "").substring(0, 16)
                        : matchingCert.getId().toString();
            }

            String unlockRequirement = hasPassed
                    ? "Certificate is being generated"
                    : "Complete the final assessment to unlock";
            // App-relative path the frontend can navigate to start the assessment
            String actionUrl = "/api/v1/learner/assessments/generate/" + trackId;

            inProgress.add(CertificateDashboardResponse.InProgressCertificate.builder()
                    .certificateId(currentCertId)
                    .displayId(currentDisplayId)
                    .trackName(track.getTitle())
                    .progress(CertificateDashboardResponse.Progress.builder()
                            .modulesCompleted(completedModules)
                            .totalModules(totalModules)
                            .build())
                    .unlockRequirement(unlockRequirement)
                    .actionUrl(actionUrl)
                    .build());
        }

        // Sort in-progress: highest module completion ratio first — UI shows index[0] as primary card
        inProgress.sort(Comparator.comparingDouble((CertificateDashboardResponse.InProgressCertificate ip) -> {
            int total = ip.getProgress().getTotalModules();
            return total == 0 ? 0.0 : (double) ip.getProgress().getModulesCompleted() / total;
        }).reversed());

        return CertificateDashboardResponse.builder()
                .learnerInfo(learnerInfo)
                .summary(CertificateDashboardResponse.Summary.builder()
                        .earnedCount(earned.size())
                        .totalAssigned(totalAssigned)
                        .build())
                .earnedCertificates(earned)
                .inProgressCertificates(inProgress)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}

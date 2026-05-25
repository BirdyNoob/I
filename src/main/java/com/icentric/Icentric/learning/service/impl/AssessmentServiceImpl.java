package com.icentric.Icentric.learning.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.LessonStepRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.assessment.*;
import com.icentric.Icentric.learning.entity.AssessmentAssignment;
import com.icentric.Icentric.learning.entity.AssessmentAttempt;
import com.icentric.Icentric.learning.entity.AssessmentConfig;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.*;
import com.icentric.Icentric.learning.service.AssessmentService;
import com.icentric.Icentric.learning.service.CertificateService;
import com.icentric.Icentric.tenant.TenantSchemaService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.audit.constants.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AssessmentServiceImpl implements AssessmentService {

    private final AssessmentConfigRepository assessmentConfigRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentAssignmentRepository assessmentAssignmentRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final TrackRepository trackRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonStepRepository lessonStepRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final UserRepository userRepository;
    private final CertificateService certificateService;
    private final ObjectMapper objectMapper;
    private final TenantSchemaService tenantSchemaService;
    private final EntityManager entityManager;
    private final AuditService auditService;

    // No cooldown: manager reset is the only way to unblock a user who exhausted retakes.

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AssessmentDashboardResponse getAssessmentDashboard(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        List<AssessmentAttempt> allAttempts       = assessmentAttemptRepository.findByUserId(userId);
        List<UserAssignment>    userAssignments    = userAssignmentRepository.findByUserId(userId);
        List<AssessmentAssignment> explicitAssigns = assessmentAssignmentRepository.findByUserId(userId);
        List<AssessmentConfig>  allConfigs         = assessmentConfigRepository.findAll();

        // Index attempts by configId
        Map<String, List<AssessmentAttempt>> attemptsByConfig = allAttempts.stream()
                .collect(Collectors.groupingBy(AssessmentAttempt::getAssessmentConfigId));

        // Index user-track assignments by trackId
        Map<UUID, UserAssignment> trackAssignmentMap = userAssignments.stream()
                .collect(Collectors.toMap(UserAssignment::getTrackId, ua -> ua, (a, b) -> a));

        // Set of directly-assigned assessment config IDs
        Set<String> explicitConfigIds = explicitAssigns.stream()
                .map(AssessmentAssignment::getAssessmentConfigId)
                .collect(Collectors.toSet());

        List<FinalAssessmentDto> finalAssessments = new ArrayList<>();
        int passedCount = 0;
        int totalScoreSum = 0;
        int bestScore = 0;
        int assessmentsAttempted = 0;

        UpcomingAssessmentDto upcoming = null;

        for (AssessmentConfig config : allConfigs) {
            String   configId  = config.getId();
            JsonNode cfgData   = config.getConfigData();

            // ── Determine which user this config is visible to ─────────────────
            boolean isExplicit = explicitConfigIds.contains(configId);
            UUID trackId = parseTrackId(config.getTrackId());
            boolean hasTrackAssignment  = trackId != null && trackAssignmentMap.containsKey(trackId);

            if (!isExplicit && !hasTrackAssignment) continue; // not relevant to this user

            // ── Resolve track name ─────────────────────────────────────────────
            final String trackName = (trackId != null)
                    ? trackRepository.findById(trackId).map(Track::getTitle).orElse(cfgData.path("trackName").asText("Track"))
                    : cfgData.path("trackName").asText("Track");



            // ── Eligibility (lesson completion) ───────────────────────────────
            ProgressStats progressStats = trackId != null
                    ? getTrackProgressStats(userId, trackId)
                    : ProgressStats.empty();
            boolean allModulesComplete = progressStats.allLessonsComplete();

            // Security Baseline: Check track completion
            boolean prerequisitesMet = allModulesComplete;
            String baseStatus = prerequisitesMet ? "AVAILABLE" : "LOCKED";

            // ── Attempt history ────────────────────────────────────────────────
            List<AssessmentAttempt> attempts = attemptsByConfig.getOrDefault(configId, Collections.emptyList());
            int attemptCount = attempts.size();

            // Best attempt = highest score
            AssessmentAttempt bestAttempt = attempts.stream()
                    .max(Comparator.comparingInt(a -> a.getScore() == null ? 0 : a.getScore()))
                    .orElse(null);

            // Latest attempt = most recent dateCompleted
            AssessmentAttempt latestAttempt = attempts.stream()
                    .filter(a -> a.getDateCompleted() != null)
                    .max(Comparator.comparing(AssessmentAttempt::getDateCompleted))
                    .orElse(bestAttempt);

            boolean hasInProgress = attempts.stream()
                    .anyMatch(a -> "IN_PROGRESS".equals(a.getStatus()));

            // ── Resolved status ────────────────────────────────────────────────
            String currentStatus = baseStatus;
            
            // Priority 1: PASSED (Terminal)
            // Priority 2: IN_PROGRESS (Active)
            // Priority 3: FAILED (Terminal, no retakes)
            // Priority 4: AVAILABLE/LOCKED (Base)

            if (bestAttempt != null) {
                assessmentsAttempted++;
                int sc = bestAttempt.getScore() != null ? bestAttempt.getScore() : 0;
                totalScoreSum += sc;
                if (sc > bestScore) bestScore = sc;

                if ("PASSED".equalsIgnoreCase(bestAttempt.getStatus())) {
                    currentStatus = "PASSED";
                    passedCount++;
                } else {
                    // All failed — check retake limit
                    String retakePolicy = cfgData.path("config").path("retakePolicy").asText("UNLIMITED");
                    if (!"UNLIMITED".equalsIgnoreCase(retakePolicy)) {
                        try {
                            int allowed = Integer.parseInt(retakePolicy);
                            if (attemptCount >= allowed) currentStatus = "FAILED";
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Override with IN_PROGRESS if we are not already PASSED or FAILED (no retakes)
            // Only allow IN_PROGRESS status if prerequisites are met
            if (hasInProgress && prerequisitesMet && !"PASSED".equals(currentStatus) && !"FAILED".equals(currentStatus)) {
                currentStatus = "IN_PROGRESS";
            }

            JsonNode configObj = cfgData.path("config");
            
            // ── Retake policy ────────────────────────────────────────────────
            // No cooldown: once the attempt limit is reached, only a manager reset unblocks the user.
            String retakePolicyText = configObj.path("retakePolicy").asText("UNLIMITED").toUpperCase();
            boolean retakeAllowed;
            Instant retakeAvailableAt = null; // Always null — no cooldown window
            try {
                int limit = Integer.parseInt(retakePolicyText);
                retakeAllowed = attemptCount < limit;
            } catch (NumberFormatException e) {
                retakeAllowed = true; // UNLIMITED
            }
            if (!allModulesComplete) {
                retakeAllowed = false;
            }
            int passingScore = configObj.path("passingScore").asInt(80);
            int totalQuestions = configObj.path("totalQuestions").asInt(15);
            int timeLimitSeconds = configObj.path("timeLimitSeconds").asInt(3600);
            int timeLimitMinutes = timeLimitSeconds / 60;

            // ── Certificate ────────────────────────────────────────────────────
            AssessmentCertificateDto certificate = null;
            if ("PASSED".equals(currentStatus) && bestAttempt != null
                    && bestAttempt.getCertificateId() != null) {
                UUID certId = UUID.fromString(bestAttempt.getCertificateId());
                certificate = issuedCertificateRepository.findById(certId)
                        .map(ic -> {
                            String rName = userRepository.findById(userId)
                                    .map(com.icentric.Icentric.identity.entity.User::getName)
                                    .orElse("Learner");
                            String displayId = ic.getVerificationToken() != null
                                    ? ic.getVerificationToken().toString().toUpperCase().replace("-", "").substring(0, 16)
                                    : ic.getId().toString();
                            return AssessmentCertificateDto.builder()
                                    .certificateId(ic.getId().toString())
                                    .displayId(displayId)
                                    .downloadUrl(ic.getDownloadUrl())
                                    .issuedAt(ic.getIssuedAt())
                                    .recipientName(rName)
                                    .trackTitle(trackName)
                                    .build();
                        })
                        .orElse(null);
            }

            // ── Topic breakdown — lesson cards from the assigned track ───────────
            // Each entry mirrors the lesson card shape the frontend already renders.
            // Shows every lesson STEP in the track as a "topic".
            List<TopicBreakdownDto> topicBreakdown = new ArrayList<>();
            if (trackId != null) {
                List<com.icentric.Icentric.content.entity.CourseModule> mods =
                        moduleRepository.findByTrackIdOrderBySortOrder(trackId);
                for (var mod : mods) {
                    List<com.icentric.Icentric.content.entity.Lesson> lessons =
                            lessonRepository.findByModuleIdOrderBySortOrder(mod.getId());
                    for (var lesson : lessons) {
                        com.icentric.Icentric.learning.entity.LessonProgress lp = lessonProgressRepository
                                .findByUserIdAndLessonId(userId, lesson.getId())
                                .orElse(null);

                        java.util.List<UUID> completedStepIds = (lp != null && lp.getCompletedStepIds() != null)
                                ? lp.getCompletedStepIds() : Collections.emptyList();
                        boolean lessonCompleted = lp != null && "COMPLETED".equals(lp.getStatus());

                        List<com.icentric.Icentric.content.entity.LessonStep> steps =
                                lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lesson.getId());

                        boolean foundInProgress = false;

                        for (var step : steps) {
                            String stepStatus;
                            if (lessonCompleted || completedStepIds.contains(step.getId())) {
                                stepStatus = "COMPLETED";
                            } else if (!foundInProgress && lp != null && "IN_PROGRESS".equals(lp.getStatus())) {
                                stepStatus = "IN_PROGRESS";
                                foundInProgress = true;
                            } else if (!foundInProgress && lp == null) {
                                stepStatus = "NOT_STARTED";
                                // The very first step of a NOT_STARTED lesson stays NOT_STARTED
                            } else {
                                stepStatus = "NOT_STARTED";
                            }

                            String meta;
                            String actionLabel;
                            switch (stepStatus) {
                                case "COMPLETED"   -> { meta = "Done";         actionLabel = "Review";   }
                                case "IN_PROGRESS" -> { meta = "In Progress";  actionLabel = "Continue"; }
                                default            -> { meta = "Not Started";  actionLabel = "Start";    }
                            }

                            topicBreakdown.add(TopicBreakdownDto.builder()
                                    .stepId(step.getId().toString())
                                    .lessonId(lesson.getId().toString())
                                    .title(step.getTitle())
                                    .status(stepStatus)
                                    .meta(meta)
                                    .actionLabel(actionLabel)
                                    .build());
                        }
                    }
                }
            }



            // ── Eligibility ────────────────────────────────────────────────────
            AssessmentEligibilityDto eligibility = null;
            if (!"PASSED".equals(currentStatus)) {
                eligibility = AssessmentEligibilityDto.builder()
                        .allModulesComplete(allModulesComplete)
                        .completedModuleCount(progressStats.completedModules())
                        .build();
            }

            // No cooldown status: retake exhaustion is surfaced as FAILED.
            // Only a manager reset (POST /attempts/reset) can unblock the user.

            FinalAssessmentDto dto = FinalAssessmentDto.builder()
                    .id(configId)
                    .trackId(trackId != null ? trackId.toString() : null)
                    .trackName(trackName)
                    .title(cfgData.path("title").asText("Final Assessment"))
                    .status(currentStatus)
                    .retakeAvailableAt(retakeAvailableAt != null ? retakeAvailableAt.toString() : null)
                    .cooldownHours(null)
                    .score(bestAttempt != null ? bestAttempt.getScore() : null)
                    .passingScore(passingScore)
                    .attemptNumber(attemptCount)
                    .completedAt(bestAttempt != null && bestAttempt.getDateCompleted() != null
                            ? bestAttempt.getDateCompleted().toString() : null)
                    .totalQuestions(totalQuestions)
                    .answeredQuestions(bestAttempt != null ? bestAttempt.getQuestionsAnswered() : null)
                    .retakeAllowed(retakeAllowed)
                    .timeLimitMinutes(timeLimitMinutes)
                    .retakePolicy(retakePolicyText)
                    .certificate(certificate)
                    .eligibility(eligibility)
                    .topicBreakdown(topicBreakdown)
                    .build();

            // ── Upcoming: first AVAILABLE or IN_PROGRESS assessment ───────────
            if (upcoming == null && ("AVAILABLE".equals(currentStatus) || "IN_PROGRESS".equals(currentStatus))) {
                Integer lastScore = latestAttempt != null ? latestAttempt.getScore() : null;
                upcoming = UpcomingAssessmentDto.builder()
                        .assessmentId(configId)
                        .title(cfgData.path("title").asText("Final Assessment"))
                        .attemptNumber(attemptCount)
                        .lastScore(lastScore)
                        .passingScore(passingScore)
                        .retakeAvailableAt(retakeAvailableAt != null ? retakeAvailableAt.toString() : null)
                        .status(currentStatus)
                        .build();
            }

            finalAssessments.add(dto);
        }

        // ── Summary stats ──────────────────────────────────────────────────────
        int averageScore = assessmentsAttempted > 0 ? totalScoreSum / assessmentsAttempted : 0;
        AssessmentStatsDto summary = AssessmentStatsDto.builder()
                .averageScore(averageScore)
                .finalAssessmentsPassed(passedCount)
                .finalAssessmentsTotal(finalAssessments.size())
                .bestScore(bestScore)
                .build();

        return AssessmentDashboardResponse.builder()
                .learnerId(userId.toString())
                .summary(summary)
                .upcoming(upcoming)
                .finalAssessments(finalAssessments)
                .build();
    }

    // ── Generate (start) assessment ────────────────────────────────────────────

    @Override
    @Transactional
    public AssessmentDataResponse generateAssessment(String trackId, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        AssessmentConfig config = assessmentConfigRepository.findAll().stream()
                .filter(c -> c.getTrackId().equals(trackId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Assessment config not found for track: " + trackId));

        assertAssessmentUnlocked(config, userId);

        JsonNode configData = config.getConfigData();

        AssessmentMetadataDto metadata = AssessmentMetadataDto.builder()
                .title(configData.path("title").asText("Final Assessment"))
                .subtitle(configData.path("subtitle").asText(""))
                .totalQuestions(configData.path("totalQuestions").asInt(15))
                .timeLimitMinutes(configData.path("timeLimitMinutes").asInt(60))
                .passingScorePercentage(configData.path("passingScorePercentage").asInt(80))
                .retakePolicy(configData.path("retakePolicy").asText("UNLIMITED"))
                .build();

        List<AssessmentQuestionDto> questions = new ArrayList<>();
        List<JsonNode> questionNodes = extractAllQuestionNodes(configData);
        for (JsonNode qNode : questionNodes) {
            try {
                AssessmentQuestionDto qDto = objectMapper.treeToValue(qNode, AssessmentQuestionDto.class);
                questions.add(qDto);
            } catch (Exception ignored) {}
        }

        return AssessmentDataResponse.builder()
                .assessment(metadata)
                .questions(questions)
                .build();
    }

    // ── Render assessment ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public AssessmentRenderResponse getAssessmentForRender(String assessmentId, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        AssessmentConfig config = assessmentConfigRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        assertAssessmentUnlocked(config, userId);

        JsonNode configData = config.getConfigData();
        JsonNode configObj = configData.path("config");

        List<AssessmentAttempt> attempts = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, assessmentId);
        int attemptNumber = attempts.size() + 1; // Default for a new attempt

        // Check if there is an IN_PROGRESS attempt to resume
        AssessmentAttempt inProgressAttempt = attempts.stream()
                .filter(a -> "IN_PROGRESS".equals(a.getStatus()))
                .max(Comparator.comparing(AssessmentAttempt::getAttemptNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        // Guard: Prevent creating new attempts if retake limit is reached or already passed
        if (inProgressAttempt == null) {
            boolean alreadyPassed = attempts.stream().anyMatch(a -> "PASSED".equalsIgnoreCase(a.getStatus()));
            if (alreadyPassed) {
                throw new RuntimeException("Assessment already passed. Retake not allowed.");
            }

            String retakePolicyText = configObj.path("retakePolicy").asText("UNLIMITED").toUpperCase();
            if (!"UNLIMITED".equals(retakePolicyText)) {
                try {
                    int limit = Integer.parseInt(retakePolicyText);
                    long completedAttempts = attempts.stream()
                            .filter(a -> "PASSED".equalsIgnoreCase(a.getStatus()) || "FAILED".equalsIgnoreCase(a.getStatus()))
                            .count();
                    if (completedAttempts >= limit) {
                        // Hard block: no cooldown. Manager must reset attempts to unblock the user.
                        throw new RuntimeException("Maximum attempts reached. Please contact your manager to reset your assessment.");
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        String resumeAttemptId = null;
        String currentStatus = "NEW";
        Integer timeRemaining = null;
        List<AnswerSubmissionDto> savedAnswers = null;

        if (inProgressAttempt != null) {
            resumeAttemptId = inProgressAttempt.getId().toString();
            currentStatus = "IN_PROGRESS";
            timeRemaining = inProgressAttempt.getTimeRemainingSeconds();
            savedAnswers = inProgressAttempt.getSavedAnswers();
            // Do not increment attempt number if we are resuming
            attemptNumber = inProgressAttempt.getAttemptNumber() != null ? inProgressAttempt.getAttemptNumber() : attemptNumber;
        } else {
            // Create a new attempt so the frontend has an attemptId for save-progress
            AssessmentAttempt newAttempt = new AssessmentAttempt();
            newAttempt.setId(UUID.randomUUID());
            newAttempt.setUserId(userId);
            newAttempt.setAssessmentConfigId(assessmentId);
            newAttempt.setAttemptNumber(attemptNumber);
            newAttempt.setStatus("IN_PROGRESS");
            newAttempt.setQuestionsAnswered(0);
            newAttempt.setScore(0);
            newAttempt.setStartedAt(Instant.now());
            assessmentAttemptRepository.save(newAttempt);

            String assessmentTitle = configData.path("title").asText("Assessment");
            auditService.log(
                    userId,
                    AuditAction.ASSESSMENT_START,
                    "ASSESSMENT",
                    assessmentId,
                    "Started final assessment: " + assessmentTitle + " (Attempt #" + attemptNumber + ")"
            );
            log.info("Assessment started: userId={}, assessmentId={}, attemptNumber={}", userId, assessmentId, attemptNumber);

            resumeAttemptId = newAttempt.getId().toString();
            // Keep currentStatus as "NEW" so the frontend knows it's a fresh start, 
            // even though the attempt is stored as IN_PROGRESS in the database.
            currentStatus = "NEW";
        }

        AssessmentRenderInfoDto info = AssessmentRenderInfoDto.builder()
                .id(config.getId())
                .title(configData.path("title").asText("Assessment"))
                .trackName(configData.path("trackName").asText(config.getTrackId()))
                .totalQuestions(configObj.path("totalQuestions").asInt())
                .timeLimitMinutes(configObj.path("timeLimitSeconds").asInt(3600) / 60)
                .passingScorePercent(configObj.path("passingScore").asInt())
                .retakesAllowed(configObj.path("retakePolicy").asText("UNLIMITED"))
                .attemptNumber(attemptNumber)
                .guidelines(List.of("You cannot pause the timer once started.",
                        "All answers are final once submitted."))
                .attemptId(resumeAttemptId)
                .status(currentStatus)
                .timeRemainingSeconds(timeRemaining)
                .savedAnswers(savedAnswers)
                .build();

        List<AssessmentRenderQuestionDto> questions = new ArrayList<>();
        List<JsonNode> questionNodes = extractAllQuestionNodes(configData);
        
        for (JsonNode qNode : questionNodes) {
            List<AssessmentRenderOptionDto> options = new ArrayList<>();
            JsonNode optionsNode = qNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode oNode : optionsNode) {
                    options.add(AssessmentRenderOptionDto.builder()
                            .optionId(oNode.path("optionId").asText())
                            .letter(oNode.path("letter").isMissingNode() ? null : oNode.path("letter").asText())
                            .text(oNode.path("text").asText())
                            .build());
                }
            }
            AssessmentImageDto imageDto = null;
            JsonNode imageNode = qNode.path("image");
            if (!imageNode.isMissingNode() && !imageNode.isNull()) {
                imageDto = AssessmentImageDto.builder()
                        .imageId(imageNode.path("imageId").asText())
                        .fileName(imageNode.path("fileName").asText())
                        .mimeType(imageNode.path("mimeType").asText())
                        .altText(imageNode.path("altText").asText())
                        .data(imageNode.path("data").asText())
                        .build();
            }

            questions.add(AssessmentRenderQuestionDto.builder()
                    .questionId(qNode.path("questionId").asText())
                    .orderIndex(qNode.path("orderIndex").asInt())
                    .type(qNode.path("type").asText())
                    .topic(qNode.path("topic").asText())
                    .difficulty(qNode.path("difficulty").isMissingNode() ? null : qNode.path("difficulty").asInt())
                    .scenarioContext(qNode.path("scenarioContext").isMissingNode() ? null : qNode.path("scenarioContext").asText())
                    .text(qNode.path("text").asText())
                    .image(imageDto)
                    .options(options)
                    .build());
        }

        return AssessmentRenderResponse.builder()
                .assessment(info)
                .questions(questions)
                .build();
    }

    // ── Save assessment progress ───────────────────────────────────────────────

    @Override
    @Transactional
    public SaveAssessmentProgressResponse saveAssessmentProgress(String assessmentId, SaveAssessmentProgressRequest request, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        AssessmentConfig config = assessmentConfigRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));
        assertAssessmentUnlocked(config, userId);

        AssessmentAttempt attempt = null;
        if (request.getAttemptId() != null && !request.getAttemptId().isBlank()) {
            try {
                UUID attemptId = UUID.fromString(request.getAttemptId());
                attempt = assessmentAttemptRepository.findById(attemptId).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }

        // If no attempt specified, look for an existing IN_PROGRESS attempt
        if (attempt == null) {
            List<AssessmentAttempt> attempts = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, assessmentId);
            attempt = attempts.stream()
                    .filter(a -> "IN_PROGRESS".equals(a.getStatus()))
                    .max(Comparator.comparing(AssessmentAttempt::getAttemptNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
        }

        // Strict Immutability Guard: finalized attempts cannot have their progress modified
        if (attempt != null && !"IN_PROGRESS".equals(attempt.getStatus())) {
            throw new IllegalStateException("This assessment attempt has already been finalized and cannot be modified.");
        }

        // Reject saving progress if no active attempt exists
        if (attempt == null) {
            throw new IllegalStateException("No active, uncompleted assessment attempt was found for this session. You must start the assessment first.");
        }

        attempt.setStatus("IN_PROGRESS");
        attempt.setSavedAnswers(request.getAnswers());
        attempt.setTimeRemainingSeconds(request.getTimeRemainingSeconds());
        attempt.setLastSavedAt(Instant.now());

        // Update total answered for stats if we want, or wait until submit
        if (request.getAnswers() != null) {
            long answeredCount = request.getAnswers().stream()
                    .filter(a -> a.getSelectedOptionId() != null && !a.getSelectedOptionId().isBlank())
                    .count();
            attempt.setQuestionsAnswered((int) answeredCount);
        }

        assessmentAttemptRepository.save(attempt);

        return SaveAssessmentProgressResponse.builder()
                .attemptId(attempt.getId().toString())
                .status(attempt.getStatus())
                .build();
    }

    // ── Submit assessment ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public SubmitAssessmentResponse submitAssessment(String assessmentId, SubmitAssessmentRequest request, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        AssessmentConfig config = assessmentConfigRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        assertAssessmentUnlocked(config, userId);

        JsonNode cfgData = config.getConfigData();
        JsonNode configObj = cfgData.path("config");
        int totalQuestions = configObj.path("totalQuestions").asInt(request.getAnswers().size());

        // ── Cooldown guard removed: users can use retakes immediately ────────
        String retakePolicyText = configObj.path("retakePolicy").asText("UNLIMITED").toUpperCase();
        List<AssessmentAttempt> priorAttempts = assessmentAttemptRepository
                .findByUserIdAndAssessmentConfigId(userId, assessmentId);
                
        boolean alreadyPassed = priorAttempts.stream()
                .anyMatch(a -> "PASSED".equalsIgnoreCase(a.getStatus()));
        // Grading using correctOptionId
        int correctCount = 0;
        List<JsonNode> questionNodes = extractAllQuestionNodes(cfgData);
        
        if (request.getAnswers() != null) {
            for (com.icentric.Icentric.learning.dto.assessment.AnswerSubmissionDto answer : request.getAnswers()) {
                String questionId = answer.getQuestionId();
                String selectedOptionId = answer.getSelectedOptionId();
                
                for (JsonNode qNode : questionNodes) {
                    if (qNode.path("questionId").asText().equals(questionId)) {
                        String correctOptionId = qNode.path("correctOptionId").asText();
                        if (correctOptionId.equals(selectedOptionId)) {
                            correctCount++;
                        }
                        break;
                    }
                }
            }
        }
        
        int score        = totalQuestions > 0 ? (int) ((correctCount * 100.0) / totalQuestions) : 0;
        int passingScore = configObj.path("passingScore").asInt(80);
        String status    = score >= passingScore ? "PASSED" : "FAILED";

        List<AssessmentAttempt> existing = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, assessmentId);
        int attemptNumber = existing.size() + 1;

        AssessmentAttempt previousAttempt = existing.stream()
                .filter(a -> a.getDateCompleted() != null)
                .max(Comparator.comparing(AssessmentAttempt::getDateCompleted))
                .orElse(null);
                
        Integer previousScore = previousAttempt != null ? previousAttempt.getScore() : null;
        Integer improvementPercent = null;
        if (previousScore != null && previousScore > 0) {
            improvementPercent = (int) Math.round(((double) (score - previousScore) / previousScore) * 100);
        } else if (previousScore != null && previousScore == 0) {
            improvementPercent = score > 0 ? 100 : 0;
        }

        // ── Persist attempt ──────────────────────────────────────────────────────
        AssessmentAttempt attempt = null;
        if (request.getAttemptId() != null && !request.getAttemptId().isBlank()) {
            try {
                UUID attemptId = UUID.fromString(request.getAttemptId());
                attempt = assessmentAttemptRepository.findById(attemptId).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
        
        // If not explicitly provided, see if there is an IN_PROGRESS attempt we should finalize
        if (attempt == null) {
            attempt = existing.stream()
                    .filter(a -> "IN_PROGRESS".equals(a.getStatus()))
                    .max(Comparator.comparing(AssessmentAttempt::getAttemptNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
        }

        // Strict Immutability Guard: finalized attempts cannot be tampered with or resubmitted
        if (attempt != null && !"IN_PROGRESS".equals(attempt.getStatus())) {
            throw new IllegalStateException("This assessment attempt has already been finalized and cannot be resubmitted.");
        }

        // Server-Side Time Limit Validation: enforce hard limit on submission time
        if (attempt != null && attempt.getStartedAt() != null) {
            int timeLimitSeconds = configObj.path("timeLimitSeconds").asInt(3600); // Default to 60 minutes
            Instant mustSubmitBy = attempt.getStartedAt().plusSeconds(timeLimitSeconds).plusSeconds(60); // 60-second grace period
            if (Instant.now().isAfter(mustSubmitBy)) {
                score = 0;
                status = "FAILED";
                log.warn("Assessment attempt {} submitted after time limit expired (Started: {}, Limit: {}s). Force-failing attempt.",
                        attempt.getId(), attempt.getStartedAt(), timeLimitSeconds);
            }
        }

        if (attempt == null) {
            throw new IllegalStateException("No active, uncompleted assessment attempt was found for this session. You must start the assessment first.");
        }

        attempt.setStatus(status);
        attempt.setScore(score);
        attempt.setDateCompleted(Instant.now());
        attempt.setQuestionsAnswered(request.getAnswers() != null ? request.getAnswers().size() : 0);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setSavedAnswers(request.getAnswers());

        
        // Optional: clear savedAnswers on submit, or keep them for historical purposes. 
        // We will keep them, but update timeRemainingSeconds to 0 since it's completed.
        if (request.getTimeTakenSeconds() != null) {
            // Since they passed timeTakenSeconds, maybe we compute timeRemaining if we want.
            // Or just set it to 0.
            attempt.setTimeRemainingSeconds(0);
        }
        
        assessmentAttemptRepository.save(attempt);

        String assessmentTitle = cfgData.path("title").asText("Assessment");
        auditService.log(
                userId,
                AuditAction.ASSESSMENT_SUBMIT,
                "ASSESSMENT",
                assessmentId,
                "Submitted final assessment: " + assessmentTitle + " - Score: " + score + "%, Status: " + status + " (Attempt #" + attempt.getAttemptNumber() + ")"
        );
        log.info("Assessment submitted: userId={}, assessmentId={}, attemptNumber={}, score={}%, status={}", userId, assessmentId, attempt.getAttemptNumber(), score, status);

        // ── Certificate: trigger real issuance via CertificateService ──────────
        CertificateResultDto certDto = null;
        if ("PASSED".equals(status) && config.getTrackId() != null && !config.getTrackId().isBlank() && !"null".equals(config.getTrackId())) {
            UUID trackUuid = null;
            try {
                trackUuid = UUID.fromString(config.getTrackId());
                certificateService.checkAndIssue(userId, trackUuid);
            } catch (Exception e) {
                // Log but don't fail the submission if certificate generation errors or UUID parsing fails
            }

            if (trackUuid != null) {
                IssuedCertificate issued = issuedCertificateRepository
                        .findByUserIdAndTrackId(userId, trackUuid)
                        .orElse(null);

                if (issued != null) {
                    // Store the real issued-certificate UUID on the attempt for dashboard lookup
                    attempt.setCertificateId(issued.getId().toString());
                    assessmentAttemptRepository.save(attempt);

                    String recipientName = userRepository.findById(userId)
                            .map(com.icentric.Icentric.identity.entity.User::getName)
                            .orElse("Learner");

                    String displayId = issued.getVerificationToken() != null
                            ? issued.getVerificationToken().toString().toUpperCase().replace("-", "").substring(0, 16)
                            : issued.getId().toString();

                    certDto = CertificateResultDto.builder()
                            .certificateId(issued.getId().toString())
                            .displayId(displayId)
                            .recipientName(recipientName)
                            .trackName(cfgData.path("trackName").asText("Track"))
                            .issuedDate(issued.getIssuedAt() != null
                                    ? issued.getIssuedAt().toString()
                                    : LocalDate.now().toString())
                            .downloadUrl(issued.getDownloadUrl())
                            .build();
                }
            }
        }

        return SubmitAssessmentResponse.builder()
                .result(AssessmentResultDto.builder()
                        .status(status)
                        .score(score)
                        .passingScore(passingScore)
                        .correctCount(correctCount)
                        .totalQuestions(totalQuestions)
                        .previousScore(previousScore)
                        .improvementPercent(improvementPercent)
                        .build())
                .certificate(certDto)
                .weakAreas(List.of())
                .build();
    }

    // ── Review assessment ──────────────────────────────────────────────────────
    
    @Override
    @Transactional(readOnly = true)
    public com.icentric.Icentric.learning.dto.assessment.AssessmentReviewResponse getAssessmentReview(String assessmentId, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        AssessmentConfig config = assessmentConfigRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        JsonNode cfgData = config.getConfigData();
        JsonNode configObj = cfgData.path("config");

        List<AssessmentAttempt> attempts = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, assessmentId);
        
        // Find best attempt that is PASSED
        AssessmentAttempt bestAttempt = attempts.stream()
                .filter(a -> "PASSED".equalsIgnoreCase(a.getStatus()))
                .max(Comparator.comparingInt(a -> a.getScore() == null ? 0 : a.getScore()))
                .orElseThrow(() -> new RuntimeException("No passed attempt found for review"));

        List<com.icentric.Icentric.learning.dto.assessment.AnswerSubmissionDto> savedAnswers = bestAttempt.getSavedAnswers();
        Map<String, String> answerMap = new HashMap<>();
        if (savedAnswers != null) {
            for (com.icentric.Icentric.learning.dto.assessment.AnswerSubmissionDto ans : savedAnswers) {
                answerMap.put(ans.getQuestionId(), ans.getSelectedOptionId());
            }
        }

        List<com.icentric.Icentric.learning.dto.assessment.AssessmentQuestionReviewDto> reviewQuestions = new ArrayList<>();
        List<JsonNode> questionNodes = extractAllQuestionNodes(cfgData);
        
        for (JsonNode qNode : questionNodes) {
            String qId = qNode.path("questionId").asText();
            String correctOptionId = qNode.path("correctOptionId").asText();
            String selectedOptionId = answerMap.get(qId);
            boolean isCorrect = correctOptionId.equals(selectedOptionId);

            List<AssessmentRenderOptionDto> options = new ArrayList<>();
            JsonNode optionsNode = qNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode oNode : optionsNode) {
                    options.add(AssessmentRenderOptionDto.builder()
                            .optionId(oNode.path("optionId").asText())
                            .letter(oNode.path("letter").isMissingNode() ? null : oNode.path("letter").asText())
                            .text(oNode.path("text").asText())
                            .build());
                }
            }

            AssessmentImageDto imageDto = null;
            JsonNode imageNode = qNode.path("image");
            if (!imageNode.isMissingNode() && !imageNode.isNull()) {
                imageDto = AssessmentImageDto.builder()
                        .imageId(imageNode.path("imageId").asText())
                        .fileName(imageNode.path("fileName").asText())
                        .mimeType(imageNode.path("mimeType").asText())
                        .altText(imageNode.path("altText").asText())
                        .data(imageNode.path("data").asText())
                        .build();
            }

            reviewQuestions.add(com.icentric.Icentric.learning.dto.assessment.AssessmentQuestionReviewDto.builder()
                    .questionId(qId)
                    .orderIndex(qNode.path("orderIndex").asInt())
                    .type(qNode.path("type").asText())
                    .topic(qNode.path("topic").asText())
                    .scenarioContext(qNode.path("scenarioContext").isMissingNode() ? null : qNode.path("scenarioContext").asText())
                    .text(qNode.path("text").asText())
                    .image(imageDto)
                    .selectedOptionId(selectedOptionId)
                    .correctOptionId(correctOptionId)
                    .isCorrect(isCorrect)
                    .explanation(qNode.path("explanation").isMissingNode() ? null : qNode.path("explanation").asText())
                    .options(options)
                    .build());
        }

        return com.icentric.Icentric.learning.dto.assessment.AssessmentReviewResponse.builder()
                .assessmentId(config.getId())
                .title(cfgData.path("title").asText("Assessment"))
                .trackName(cfgData.path("trackName").asText(config.getTrackId()))
                .score(bestAttempt.getScore())
                .passingScore(configObj.path("passingScore").asInt())
                .status(bestAttempt.getStatus())
                .completedAt(bestAttempt.getDateCompleted() != null ? bestAttempt.getDateCompleted().toString() : null)
                .questions(reviewQuestions)
                .build();
    }

    // ── Admin: create config ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void createAssessmentConfig(String trackId, JsonNode request) {
        // No search_path override needed — @Table(schema = "system") ensures Hibernate
        // always targets the global system.assessment_config table regardless of caller.
        AssessmentConfig config = new AssessmentConfig();
        config.setId(request.path("assessmentId").asText());
        config.setTrackId(trackId);
        
        if (request instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("trackId", trackId);
        }
        
        config.setConfigData(request);
        assessmentConfigRepository.save(config);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AdminAssessmentConfigDto> getAllAssessmentConfigs() {
        // system.assessment_config is global — no search_path override needed.
        return assessmentConfigRepository.findAll().stream().map(config -> {
            JsonNode cfgData = config.getConfigData();
            return AdminAssessmentConfigDto.builder()
                    .id(config.getId())
                    .trackId(config.getTrackId())
                    .title(cfgData.path("title").asText())
                    .trackName(cfgData.path("trackName").asText())
                    .config(cfgData.path("config"))
                    .createdAt(config.getCreatedAt())
                    .updatedAt(config.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
    
    private List<JsonNode> extractAllQuestionNodes(JsonNode configData) {
        List<JsonNode> allQuestions = new ArrayList<>();
        // Root level questions
        JsonNode rootQuestions = configData.path("questions");
        if (rootQuestions.isArray()) {
            for (JsonNode qNode : rootQuestions) {
                allQuestions.add(qNode);
            }
        }
        // Section level questions
        JsonNode sectionsNode = configData.path("sections");
        if (sectionsNode.isArray()) {
            for (JsonNode section : sectionsNode) {
                JsonNode sectionQuestions = section.path("questions");
                if (sectionQuestions.isArray()) {
                    for (JsonNode qNode : sectionQuestions) {
                        allQuestions.add(qNode);
                    }
                }
            }
        }
        return allQuestions;
    }

    private void assertAssessmentUnlocked(AssessmentConfig config, UUID userId) {
        UUID trackId = parseTrackId(config.getTrackId());
        log.debug("Checking assessment unlock status for user {} on track {}", userId, trackId);
        
        if (trackId == null) {
            log.warn("Assessment config {} has no valid trackId. Defaulting to LOCKED.", config.getId());
            throw new AccessDeniedException("This assessment is not linked to a valid track.");
        }
        
        ProgressStats stats = getTrackProgressStats(userId, trackId);
        if (!stats.allLessonsComplete()) {
            log.info("Access denied to assessment for user {}: Incomplete track {}. Progress: {}/{} lessons", 
                    userId, trackId, stats.completedLessons(), stats.totalLessons());
            throw new AccessDeniedException("Complete all lessons in this track before taking the assessment.");
        }
    }

    private ProgressStats getTrackProgressStats(UUID userId, UUID trackId) {
        List<com.icentric.Icentric.content.entity.CourseModule> modules =
                moduleRepository.findByTrackIdOrderBySortOrder(trackId);

        int completedModules = 0;
        int totalLessons = 0;
        int completedLessons = 0;

        for (var module : modules) {
            List<com.icentric.Icentric.content.entity.Lesson> lessons =
                    lessonRepository.findByModuleIdOrderBySortOrder(module.getId());
            int moduleLessonCount = lessons.size();
            int moduleCompletedLessons = 0;
            totalLessons += moduleLessonCount;

            for (var lesson : lessons) {
                boolean completed = lessonProgressRepository.existsByUserIdAndLessonIdAndStatus(
                        userId, lesson.getId(), "COMPLETED");
                if (completed) {
                    completedLessons++;
                    moduleCompletedLessons++;
                }
            }

            if (moduleLessonCount > 0 && moduleCompletedLessons >= moduleLessonCount) {
                completedModules++;
            }
        }

        boolean allLessonsComplete = totalLessons > 0 && completedLessons >= totalLessons;
        
        log.debug("Track Progress Stats for user {} on track {}: modules={}, completedModules={}, lessons={}/{}, complete={}",
                userId, trackId, modules.size(), completedModules, completedLessons, totalLessons, allLessonsComplete);
                
        return new ProgressStats(modules.size(), completedModules, totalLessons, completedLessons, allLessonsComplete);
    }

    private UUID parseTrackId(String trackId) {
        if (trackId == null || trackId.isBlank() || "null".equalsIgnoreCase(trackId)) {
            return null;
        }
        try {
            return UUID.fromString(trackId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ProgressStats(
            int totalModules,
            int completedModules,
            int totalLessons,
            int completedLessons,
            boolean allLessonsComplete
    ) {
        private static ProgressStats empty() {
            return new ProgressStats(0, 0, 0, 0, false);
        }
    }

    /**
     * Sets the PostgreSQL search_path to the given tenant's schema.
     * Used by platform-admin endpoints where the JWT tenant is "system"
     * but the target data lives in a tenant schema.
     */
    private void applyTenantSchema(String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("tenantSlug must be provided");
        }
        if (!tenantSlug.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid tenant slug: " + tenantSlug);
        }
        String schema = "tenant_" + tenantSlug;
        entityManager.createNativeQuery("SET search_path TO " + schema).executeUpdate();
    }
}

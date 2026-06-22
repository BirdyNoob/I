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
import com.icentric.Icentric.learning.entity.Assessment;
import com.icentric.Icentric.learning.entity.AssessmentQuestion;
import com.icentric.Icentric.learning.entity.AssessmentSection;
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

import com.icentric.Icentric.learning.service.XpService;

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
    private final XpService xpService;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;

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

            // ── Topic breakdown — module-level progress for the assigned track ──
            List<TopicBreakdownDto> topicBreakdown = new ArrayList<>();
            if (trackId != null) {
                List<com.icentric.Icentric.content.entity.CourseModule> mods =
                        moduleRepository.findByTrackIdOrderBySortOrder(trackId);
                for (var mod : mods) {
                    List<com.icentric.Icentric.content.entity.Lesson> lessons =
                            lessonRepository.findByModuleIdOrderBySortOrder(mod.getId());
                    int totalLessons = lessons.size();
                    int completedLessons = 0;
                    boolean modHasInProgress = false;

                    for (var lesson : lessons) {
                        com.icentric.Icentric.learning.entity.LessonProgress lp = lessonProgressRepository
                                .findByUserIdAndLessonId(userId, lesson.getId())
                                .orElse(null);
                        if (lp != null && "COMPLETED".equals(lp.getStatus())) {
                            completedLessons++;
                        } else if (lp != null && "IN_PROGRESS".equals(lp.getStatus())) {
                            modHasInProgress = true;
                        }
                    }

                    String modStatus;
                    String meta;
                    String actionLabel;
                    if (completedLessons == totalLessons && totalLessons > 0) {
                        modStatus = "COMPLETED";
                        meta = completedLessons + "/" + totalLessons + " lessons";
                        actionLabel = "Review";
                    } else if (completedLessons > 0 || modHasInProgress) {
                        modStatus = "IN_PROGRESS";
                        meta = completedLessons + "/" + totalLessons + " lessons";
                        actionLabel = "Continue";
                    } else {
                        modStatus = "NOT_STARTED";
                        meta = "Not Started";
                        actionLabel = "Start";
                    }

                    topicBreakdown.add(TopicBreakdownDto.builder()
                            .moduleId(mod.getId().toString())
                            .title(mod.getTitle())
                            .status(modStatus)
                            .meta(meta)
                            .actionLabel(actionLabel)
                            .build());
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
        List<String> selectedQuestionIds = null;

        if (inProgressAttempt != null) {
            resumeAttemptId = inProgressAttempt.getId().toString();
            currentStatus = "IN_PROGRESS";
            timeRemaining = inProgressAttempt.getTimeRemainingSeconds();
            savedAnswers = inProgressAttempt.getSavedAnswers();
            selectedQuestionIds = inProgressAttempt.getSelectedQuestionIds();
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

            // Shuffle and select 50 questions from the question bank
            List<JsonNode> allQuestionNodes = extractAllQuestionNodes(configData);
            List<String> allIds = allQuestionNodes.stream()
                    .map(q -> q.path("questionId").asText())
                    .collect(Collectors.toList());
            Collections.shuffle(allIds);
            int selectCount = Math.min(50, allIds.size());
            selectedQuestionIds = allIds.subList(0, selectCount);
            newAttempt.setSelectedQuestionIds(new ArrayList<>(selectedQuestionIds));

            assessmentAttemptRepository.save(newAttempt);

            String assessmentTitle = configData.path("title").asText("Assessment");
            auditService.log(
                    userId,
                    AuditAction.ASSESSMENT_START,
                    "ASSESSMENT",
                    assessmentId,
                    "Started final assessment: " + assessmentTitle + " (Attempt #" + attemptNumber + ")"
            );
            log.info("Assessment started: userId={}, assessmentId={}, attemptNumber={}, questionsSelected={}", userId, assessmentId, attemptNumber, selectCount);

            resumeAttemptId = newAttempt.getId().toString();
            currentStatus = "NEW";
        }

        AssessmentRenderInfoDto info = AssessmentRenderInfoDto.builder()
                .id(config.getId())
                .title(configData.path("title").asText("Assessment"))
                .trackName(configData.path("trackName").asText(config.getTrackId()))
                .totalQuestions(selectedQuestionIds != null ? selectedQuestionIds.size() : configObj.path("totalQuestions").asInt())
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

        // Filter to only the selected 50 questions for this attempt
        Set<String> selectedSet = selectedQuestionIds != null ? new HashSet<>(selectedQuestionIds) : Collections.emptySet();
        
        for (JsonNode qNode : questionNodes) {
            String qId = qNode.path("questionId").asText();
            if (!selectedSet.isEmpty() && !selectedSet.contains(qId)) continue;
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
        // Grading using correctOptionId — only grade selected questions
        int correctCount = 0;
        List<JsonNode> questionNodes = extractAllQuestionNodes(cfgData);

        // Resolve the attempt early to get selectedQuestionIds for grading
        AssessmentAttempt attempt = null;
        if (request.getAttemptId() != null && !request.getAttemptId().isBlank()) {
            try {
                UUID attemptId = UUID.fromString(request.getAttemptId());
                attempt = assessmentAttemptRepository.findById(attemptId).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
        if (attempt == null) {
            attempt = priorAttempts.stream()
                    .filter(a -> "IN_PROGRESS".equals(a.getStatus()))
                    .max(Comparator.comparing(AssessmentAttempt::getAttemptNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
        }

        // Filter questions to only the selected set for this attempt
        Set<String> selectedSet = (attempt != null && attempt.getSelectedQuestionIds() != null)
                ? new HashSet<>(attempt.getSelectedQuestionIds()) : Collections.emptySet();
        
        // Use selected count as totalQuestions for scoring
        int gradedTotal = selectedSet.isEmpty() ? totalQuestions : selectedSet.size();

        if (request.getAnswers() != null) {
            for (com.icentric.Icentric.learning.dto.assessment.AnswerSubmissionDto answer : request.getAnswers()) {
                String questionId = answer.getQuestionId();
                String selectedOptionId = answer.getSelectedOptionId();
                
                // Only grade if this question is in the selected set (or no selection exists for backward compat)
                if (!selectedSet.isEmpty() && !selectedSet.contains(questionId)) continue;
                
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
        
        int score        = gradedTotal > 0 ? (int) ((correctCount * 100.0) / gradedTotal) : 0;
        int passingScore = configObj.path("passingScore").asInt(80);
        String status    = score >= passingScore ? "PASSED" : "FAILED";

        List<AssessmentAttempt> existing = priorAttempts;
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
        // attempt already resolved above for grading

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
        attempt.setTotalQuestions(gradedTotal);
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

        if ("PASSED".equals(status)) {
            boolean isFirstAttempt = (attempt.getAttemptNumber() == null || attempt.getAttemptNumber() == 1);
            try {
                xpService.triggerXpEvent(userId, "QUIZ_PASSED", "ASSESSMENT", UUID.fromString(assessmentId), score, isFirstAttempt);
            } catch (Exception e) {
                log.error("Failed to trigger XP event for quiz passed, userId: {}", userId, e);
            }
        }

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

        // Filter to only the selected questions for this attempt
        Set<String> selectedSet = (bestAttempt.getSelectedQuestionIds() != null)
                ? new HashSet<>(bestAttempt.getSelectedQuestionIds()) : Collections.emptySet();
        
        for (JsonNode qNode : questionNodes) {
            String qId = qNode.path("questionId").asText();
            if (!selectedSet.isEmpty() && !selectedSet.contains(qId)) continue;
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
        // Persist to legacy assessment_config (JSONB) for backward compat
        AssessmentConfig config = new AssessmentConfig();
        config.setId(request.path("assessmentId").asText());
        config.setTrackId(trackId);
        
        if (request instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("trackId", trackId);
        }
        
        config.setConfigData(request);
        assessmentConfigRepository.save(config);

        // Also persist to normalized relational tables
        importToRelationalTables(request, trackId);
    }

    private void importToRelationalTables(JsonNode json, String trackId) {
        String assessmentId = json.path("assessmentId").asText();
        if (assessmentId == null || assessmentId.isBlank()) return;

        // Delete existing if re-importing
        assessmentRepository.findById(assessmentId).ifPresent(assessmentRepository::delete);

        Assessment assessment = new Assessment();
        assessment.setId(assessmentId);
        assessment.setTrackId(trackId);
        assessment.setTitle(json.path("title").asText("Assessment"));
        assessment.setSubtitle(json.path("subtitle").asText(null));
        assessment.setTrackName(json.path("trackName").asText(null));

        JsonNode configObj = json.path("config");
        if (!configObj.isMissingNode()) {
            assessment.setTotalQuestions(configObj.path("totalQuestions").asInt(50));
            assessment.setTimeLimitSeconds(configObj.path("timeLimitSeconds").asInt(3600));
            assessment.setPassingScore(configObj.path("passingScore").asInt(80));
            assessment.setRetakePolicy(configObj.path("retakePolicy").asText("UNLIMITED"));
        }

        int questionOrder = 0;
        JsonNode sectionsNode = json.path("sections");
        if (sectionsNode.isArray()) {
            int sectionOrder = 0;
            for (JsonNode sectionNode : sectionsNode) {
                AssessmentSection section = new AssessmentSection();
                section.setAssessment(assessment);
                section.setTitle(sectionNode.path("sectionTitle").asText("Section"));
                section.setSortOrder(sectionOrder++);
                assessment.getSections().add(section);

                JsonNode questions = sectionNode.path("questions");
                if (questions.isArray()) {
                    for (JsonNode qNode : questions) {
                        questionOrder = importQuestion(assessment, section, qNode, questionOrder);
                    }
                }
            }
        }

        // Root-level questions (no section)
        JsonNode rootQuestions = json.path("questions");
        if (rootQuestions.isArray()) {
            for (JsonNode qNode : rootQuestions) {
                questionOrder = importQuestion(assessment, null, qNode, questionOrder);
            }
        }

        assessmentRepository.save(assessment);
    }

    private int importQuestion(Assessment assessment, AssessmentSection section, JsonNode qNode, int order) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setAssessment(assessment);
        question.setSection(section);
        question.setQuestionId(qNode.path("questionId").asText());
        question.setType(qNode.path("type").asText("MULTIPLE_CHOICE"));
        question.setTopic(qNode.path("topic").asText(null));
        question.setDifficulty(qNode.path("difficulty").isMissingNode() ? null : qNode.path("difficulty").asInt());
        question.setScenarioContext(qNode.path("scenarioContext").isMissingNode() ? null : qNode.path("scenarioContext").asText());
        question.setText(qNode.path("text").asText());
        question.setCorrectOptionId(qNode.path("correctOptionId").asText());
        question.setExplanation(qNode.path("explanation").isMissingNode() ? null : qNode.path("explanation").asText());
        question.setSortOrder(order++);

        // Options
        JsonNode optionsNode = qNode.path("options");
        if (optionsNode.isArray()) {
            int optOrder = 0;
            for (JsonNode oNode : optionsNode) {
                com.icentric.Icentric.learning.entity.AssessmentOption opt = new com.icentric.Icentric.learning.entity.AssessmentOption();
                opt.setQuestion(question);
                opt.setOptionId(oNode.path("optionId").asText());
                opt.setText(oNode.path("text").asText());
                opt.setExplanation(oNode.path("explanation").isMissingNode() ? null : oNode.path("explanation").asText());
                opt.setSortOrder(optOrder++);
                question.getOptions().add(opt);
            }
        }

        // Image (store base64 as bytea)
        JsonNode imageNode = qNode.path("image");
        if (!imageNode.isMissingNode() && !imageNode.isNull()) {
            com.icentric.Icentric.learning.entity.AssessmentImage img = new com.icentric.Icentric.learning.entity.AssessmentImage();
            img.setQuestion(question);
            img.setFileName(imageNode.path("fileName").asText(null));
            img.setMimeType(imageNode.path("mimeType").asText(null));
            img.setAltText(imageNode.path("altText").asText(null));
            String base64Data = imageNode.path("data").asText(null);
            if (base64Data != null && !base64Data.isBlank()) {
                img.setData(java.util.Base64.getDecoder().decode(base64Data));
            }
            String imageUrl = imageNode.path("url").asText(null);
            if (imageUrl != null) img.setImageUrl(imageUrl);
            question.setImage(img);
        }

        assessment.getQuestions().add(question);
        return order;
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
        // Try relational tables first
        String assessmentId = configData.path("assessmentId").asText(null);
        if (assessmentId != null && assessmentRepository.existsById(assessmentId)) {
            return loadQuestionsFromRelationalTables(assessmentId);
        }
        // Fallback to JSON blob for unmigrated assessments
        return extractQuestionsFromJson(configData);
    }

    private List<JsonNode> extractQuestionsFromJson(JsonNode configData) {
        List<JsonNode> allQuestions = new ArrayList<>();
        JsonNode rootQuestions = configData.path("questions");
        if (rootQuestions.isArray()) {
            for (JsonNode qNode : rootQuestions) {
                allQuestions.add(qNode);
            }
        }
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

    private List<JsonNode> loadQuestionsFromRelationalTables(String assessmentId) {
        List<AssessmentQuestion> questions = assessmentQuestionRepository.findByAssessmentIdOrderBySortOrder(assessmentId);
        List<JsonNode> nodes = new ArrayList<>();
        for (AssessmentQuestion q : questions) {
            var obj = objectMapper.createObjectNode();
            obj.put("questionId", q.getQuestionId());
            obj.put("type", q.getType());
            obj.put("topic", q.getTopic());
            obj.put("text", q.getText());
            obj.put("correctOptionId", q.getCorrectOptionId());
            obj.put("orderIndex", q.getSortOrder());
            if (q.getDifficulty() != null) obj.put("difficulty", q.getDifficulty());
            if (q.getScenarioContext() != null) obj.put("scenarioContext", q.getScenarioContext());
            if (q.getExplanation() != null) obj.put("explanation", q.getExplanation());

            var optionsArr = obj.putArray("options");
            if (q.getOptions() != null) {
                for (var opt : q.getOptions()) {
                    var optObj = objectMapper.createObjectNode();
                    optObj.put("optionId", opt.getOptionId());
                    optObj.put("text", opt.getText());
                    if (opt.getExplanation() != null) optObj.put("explanation", opt.getExplanation());
                    optionsArr.add(optObj);
                }
            }

            if (q.getImage() != null) {
                var imgObj = objectMapper.createObjectNode();
                var img = q.getImage();
                imgObj.put("imageId", img.getId().toString());
                if (img.getFileName() != null) imgObj.put("fileName", img.getFileName());
                if (img.getMimeType() != null) imgObj.put("mimeType", img.getMimeType());
                if (img.getAltText() != null) imgObj.put("altText", img.getAltText());
                if (img.getImageUrl() != null) {
                    imgObj.put("url", img.getImageUrl());
                } else if (img.getData() != null) {
                    imgObj.put("data", java.util.Base64.getEncoder().encodeToString(img.getData()));
                }
                obj.set("image", imgObj);
            }

            nodes.add(obj);
        }
        return nodes;
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
        entityManager.createNativeQuery("SET search_path TO \"" + schema + "\"").executeUpdate();
    }
}

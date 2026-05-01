package com.icentric.Icentric.learning.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.LessonStepRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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

    /** Platform-wide retake cooldown: learners must wait this long after a failed attempt. */
    private static final int RETAKE_COOLDOWN_HOURS = 24;

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
            UUID trackId = null;
            if (config.getTrackId() != null && !config.getTrackId().isBlank()
                    && !"null".equals(config.getTrackId())) {
                try { trackId = UUID.fromString(config.getTrackId()); } catch (Exception ignored) {}
            }

            boolean hasTrackAssignment  = trackId != null && trackAssignmentMap.containsKey(trackId);
            UserAssignment trackAssign  = hasTrackAssignment ? trackAssignmentMap.get(trackId) : null;

            if (!isExplicit && !hasTrackAssignment) continue; // not relevant to this user

            // ── Resolve track name ─────────────────────────────────────────────
            final String trackName = (trackId != null)
                    ? trackRepository.findById(trackId).map(Track::getTitle).orElse(cfgData.path("trackName").asText("Track"))
                    : cfgData.path("trackName").asText("Track");



            // ── Eligibility (module completion) ────────────────────────────────
            int totalModules     = 0;
            int completedModules = 0;
            if (trackId != null) {
                List<com.icentric.Icentric.content.entity.CourseModule> modules =
                        moduleRepository.findByTrackIdOrderBySortOrder(trackId);
                totalModules = modules.size();
                for (var mod : modules) {
                    long totalLessons     = lessonRepository.findByModuleIdOrderBySortOrder(mod.getId()).size();
                    long completedLessons = totalLessons == 0 ? 0 :
                            lessonProgressRepository.countCompletedLessons(userId, trackId);
                    if (totalLessons > 0 && completedLessons >= totalLessons) completedModules++;
                }
            }
            boolean allModulesComplete = totalModules > 0 && completedModules >= totalModules;

            // ── Base status ────────────────────────────────────────────────────
            // available: explicitly assigned OR track is COMPLETED
            // locked:    on track but not yet completed
            boolean trackCompleted = trackAssign != null && trackAssign.getStatus() == AssignmentStatus.COMPLETED;
            String baseStatus = (isExplicit || trackCompleted) ? "AVAILABLE" : "LOCKED";

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
                    String retakePolicy = cfgData.path("retakePolicy").asText("UNLIMITED");
                    if (!"UNLIMITED".equalsIgnoreCase(retakePolicy)) {
                        try {
                            int allowed = Integer.parseInt(retakePolicy);
                            if (attemptCount >= allowed) currentStatus = "FAILED";
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Override with IN_PROGRESS if we are not already PASSED or FAILED (no retakes)
            if (hasInProgress && !"PASSED".equals(currentStatus) && !"FAILED".equals(currentStatus)) {
                currentStatus = "IN_PROGRESS";
            }

            JsonNode configObj = cfgData.path("config");
            
            // ── Retake policy & cooldown (24h enforced in code) ───────────────────
            String retakePolicyText = configObj.path("retakePolicy").asText("UNLIMITED").toUpperCase();
            boolean retakeAllowed;
            try {
                int limit = Integer.parseInt(retakePolicyText);
                retakeAllowed = attemptCount < limit;
            } catch (NumberFormatException e) {
                retakeAllowed = true; // UNLIMITED
            }

            // Check if still within the platform-wide cooldown window (skip if UNLIMITED)
            Instant retakeAvailableAt = null;
            if (retakeAllowed && !"UNLIMITED".equals(retakePolicyText) && latestAttempt != null && latestAttempt.getDateCompleted() != null) {
                Instant cooldownEnd = latestAttempt.getDateCompleted()
                        .plus(RETAKE_COOLDOWN_HOURS, ChronoUnit.HOURS);
                if (Instant.now().isBefore(cooldownEnd)) {
                    retakeAvailableAt = cooldownEnd;
                }
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
                            return AssessmentCertificateDto.builder()
                                    .certificateId(ic.getId().toString())
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
                        .completedModuleCount(completedModules)
                        .build();
            }

            // Apply COOLDOWN status override if retakeAvailableAt is set
            if (retakeAvailableAt != null && !"PASSED".equals(currentStatus) && !"FAILED".equals(currentStatus) && !"IN_PROGRESS".equals(currentStatus)) {
                currentStatus = "COOLDOWN";
            }

            FinalAssessmentDto dto = FinalAssessmentDto.builder()
                    .id(configId)
                    .trackId(trackId != null ? trackId.toString() : null)
                    .trackName(trackName)
                    .title(cfgData.path("title").asText("Final Assessment"))
                    .status(currentStatus)
                    .retakeAvailableAt(retakeAvailableAt != null ? retakeAvailableAt.toString() : null)
                    .cooldownHours(retakeAvailableAt != null ? RETAKE_COOLDOWN_HOURS : null)
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
            if (upcoming == null && ("AVAILABLE".equals(currentStatus) || "COOLDOWN".equals(currentStatus) || "IN_PROGRESS".equals(currentStatus))) {
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
    @Transactional(readOnly = true)
    public AssessmentRenderResponse getAssessmentForRender(String assessmentId, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        AssessmentConfig config = assessmentConfigRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        JsonNode configData = config.getConfigData();
        JsonNode configObj = configData.path("config");

        List<AssessmentAttempt> attempts = assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, assessmentId);
        int attemptNumber = attempts.size() + 1; // Default for a new attempt

        // Check if there is an IN_PROGRESS attempt to resume
        AssessmentAttempt inProgressAttempt = attempts.stream()
                .filter(a -> "IN_PROGRESS".equals(a.getStatus()))
                .max(Comparator.comparing(AssessmentAttempt::getAttemptNumber, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

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
            questions.add(AssessmentRenderQuestionDto.builder()
                    .questionId(qNode.path("questionId").asText())
                    .orderIndex(qNode.path("orderIndex").asInt())
                    .type(qNode.path("type").asText())
                    .topic(qNode.path("topic").asText())
                    .difficulty(qNode.path("difficulty").isMissingNode() ? null : qNode.path("difficulty").asInt())
                    .scenarioContext(qNode.path("scenarioContext").isMissingNode() ? null : qNode.path("scenarioContext").asText())
                    .text(qNode.path("text").asText())
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
            
            // If still no attempt, create a new one
            if (attempt == null) {
                attempt = new AssessmentAttempt();
                attempt.setId(UUID.randomUUID());
                attempt.setUserId(userId);
                attempt.setAssessmentConfigId(assessmentId);
                attempt.setAttemptNumber(attempts.size() + 1);
            }
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

        JsonNode cfgData = config.getConfigData();
        JsonNode configObj = cfgData.path("config");
        int totalQuestions = configObj.path("totalQuestions").asInt(request.getAnswers().size());

        // ── Cooldown guard (24h platform rule, skipped if UNLIMITED or already PASSED) ────────
        String retakePolicyText = configObj.path("retakePolicy").asText("UNLIMITED").toUpperCase();
        List<AssessmentAttempt> priorAttempts = assessmentAttemptRepository
                .findByUserIdAndAssessmentConfigId(userId, assessmentId);
                
        boolean alreadyPassed = priorAttempts.stream()
                .anyMatch(a -> "PASSED".equalsIgnoreCase(a.getStatus()));

        if (!"UNLIMITED".equals(retakePolicyText) && !alreadyPassed) {
            priorAttempts.stream()
                    .filter(a -> a.getDateCompleted() != null)
                    .max(Comparator.comparing(AssessmentAttempt::getDateCompleted))
                    .ifPresent(last -> {
                        Instant cooldownEnd = last.getDateCompleted()
                                .plus(RETAKE_COOLDOWN_HOURS, ChronoUnit.HOURS);
                        if (Instant.now().isBefore(cooldownEnd)) {
                            throw new RuntimeException(
                                "Assessment retake not available yet. Retry after: " + cooldownEnd);
                        }
                    });
        }

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

        if (attempt == null) {
            attempt = new AssessmentAttempt();
            attempt.setId(UUID.randomUUID());
            attempt.setUserId(userId);
            attempt.setAssessmentConfigId(assessmentId);
            attempt.setAttemptNumber(attemptNumber);
        }

        attempt.setStatus(status);
        attempt.setScore(score);
        attempt.setDateCompleted(Instant.now());
        attempt.setQuestionsAnswered(request.getAnswers() != null ? request.getAnswers().size() : 0);
        attempt.setTotalQuestions(totalQuestions);
        
        // Optional: clear savedAnswers on submit, or keep them for historical purposes. 
        // We will keep them, but update timeRemainingSeconds to 0 since it's completed.
        if (request.getTimeTakenSeconds() != null) {
            // Since they passed timeTakenSeconds, maybe we compute timeRemaining if we want.
            // Or just set it to 0.
            attempt.setTimeRemainingSeconds(0);
        }
        
        assessmentAttemptRepository.save(attempt);

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

                    certDto = CertificateResultDto.builder()
                            .id(issued.getId().toString())
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

    // ── Admin: create config ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void createAssessmentConfig(String trackId, JsonNode request) {
        tenantSchemaService.applyCurrentTenantSearchPath();

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
        tenantSchemaService.applyCurrentTenantSearchPath();
        
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
}

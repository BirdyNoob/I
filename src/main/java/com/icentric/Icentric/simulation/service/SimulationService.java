package com.icentric.Icentric.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.simulation.dto.*;
import com.icentric.Icentric.simulation.entity.Simulation;
import com.icentric.Icentric.simulation.entity.SimulationAttempt;
import com.icentric.Icentric.simulation.repository.SimulationAttemptRepository;
import com.icentric.Icentric.simulation.repository.SimulationRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SimulationService {

    private static final int PASS_THRESHOLD = 70;

    private final SimulationRepository repository;
    private final SimulationAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public SimulationService(SimulationRepository repository,
                             SimulationAttemptRepository attemptRepository,
                             ObjectMapper objectMapper,
                             AuditService auditService) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    // ── Learner: List (summary only) ──────────────────────────────────────

    public SimulationListResponse listSimulations(UUID userId) {
        List<Simulation> all = repository.findAll();
        SimulationListResponse response = new SimulationListResponse();
        List<SimulationListResponse.SimulationSummary> summaries = new ArrayList<>();

        for (Simulation sim : all) {
            SimulationDetailResponse detail = parseData(sim);
            SimulationListResponse.SimulationSummary summary = new SimulationListResponse.SimulationSummary();
            summary.setId(detail.getId());
            summary.setTitle(detail.getTitle());
            summary.setAccentColor(detail.getAccentColor());
            summary.setTotalDecisions(detail.getTotalDecisions());
            summary.setAttempted(attemptRepository.existsByUserIdAndSimId(userId, detail.getId()));

            SimulationListResponse.Badge badge = new SimulationListResponse.Badge();
            badge.setIcon(detail.getBadge().getIcon());
            badge.setLabel(detail.getBadge().getLabel());
            badge.setColor(detail.getBadge().getColor());
            summary.setBadge(badge);

            SimulationListResponse.IntroSummary introSummary = new SimulationListResponse.IntroSummary();
            introSummary.setIcon(detail.getIntro().getIcon());
            introSummary.setTagLine(detail.getIntro().getTagLine());
            introSummary.setHeading(detail.getIntro().getHeading());
            List<SimulationListResponse.MetaItem> metaItems = new ArrayList<>();
            if (detail.getIntro().getMeta() != null) {
                for (SimulationDetailResponse.MetaItem m : detail.getIntro().getMeta()) {
                    SimulationListResponse.MetaItem mi = new SimulationListResponse.MetaItem();
                    mi.setIcon(m.getIcon());
                    mi.setLabel(m.getLabel());
                    metaItems.add(mi);
                }
            }
            introSummary.setMeta(metaItems);
            summary.setIntro(introSummary);
            summaries.add(summary);
        }

        response.setSimulations(summaries);
        return response;
    }

    // ── Learner: Detail (stripped of answers/feedback) ────────────────────

    public LearnerSimulationDetailResponse getSimulationForLearner(String simId) {
        SimulationDetailResponse full = getFullSimulation(simId);
        return stripSensitiveData(full);
    }

    // ── Learner: Answer single scene ─────────────────────────────────────

    public SceneAnswerResponse answerScene(String simId, String sceneId, String answer, UUID userId) {
        if (attemptRepository.existsByUserIdAndSimId(userId, simId)) {
            throw new IllegalStateException("Simulation already exists as completed. You cannot answer again.");
        }

        SimulationDetailResponse full = getFullSimulation(simId);
        SimulationDetailResponse.Scene scene = full.getScenes().stream()
                .filter(s -> s.getId().equals(sceneId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Scene not found: " + sceneId));

        String correctAnswer = scene.getQuestion().getCorrectAnswer();
        boolean isCorrect = correctAnswer.equalsIgnoreCase(answer);

        SceneAnswerResponse response = new SceneAnswerResponse();
        response.setCorrect(isCorrect);
        response.setCorrectAnswer(correctAnswer);

        if (isCorrect) {
            response.setFeedbackTitle(scene.getFeedback().getCorrect().getTitle());
            response.setFeedbackText(scene.getFeedback().getCorrect().getText());
        } else {
            String key = answer.toUpperCase();
            SimulationDetailResponse.FeedbackEntry wrongEntry = scene.getFeedback().getWrong().get(key);
            if (wrongEntry != null) {
                response.setFeedbackTitle(wrongEntry.getTitle());
                response.setFeedbackText(wrongEntry.getText());
            }
        }

        return response;
    }

    // ── Learner: Review completed simulation ─────────────────────────────

    public SimulationReviewResponse reviewSimulation(String simId, UUID userId) {
        SimulationAttempt attempt = attemptRepository.findByUserIdAndSimId(userId, simId)
                .orElseThrow(() -> new NoSuchElementException("No completed attempt found for this simulation."));

        SimulationDetailResponse full = getFullSimulation(simId);
        Map<String, String> answers;
        try {
            answers = objectMapper.readValue(attempt.getAnswers(), Map.class);
        } catch (Exception e) {
            answers = Map.of();
        }

        SimulationReviewResponse response = new SimulationReviewResponse();
        response.setId(full.getId());
        response.setTitle(full.getTitle());
        response.setAccentColor(full.getAccentColor());
        response.setBadge(full.getBadge());
        response.setTotalDecisions(full.getTotalDecisions());
        response.setIntro(full.getIntro());

        // Build review scenes (full mockups + user answer + feedback)
        List<SimulationReviewResponse.ReviewScene> reviewScenes = new ArrayList<>();
        for (SimulationDetailResponse.Scene scene : full.getScenes()) {
            SimulationReviewResponse.ReviewScene rs = new SimulationReviewResponse.ReviewScene();
            rs.setId(scene.getId());
            rs.setOrder(scene.getOrder());
            rs.setContextBar(scene.getContextBar());
            rs.setMockup(scene.getMockup());
            rs.setFeedback(scene.getFeedback());
            rs.setNextButtonText(scene.getNextButtonText());

            String correctAnswer = scene.getQuestion().getCorrectAnswer();
            String userAnswer = answers.getOrDefault(scene.getId(), "");

            SimulationReviewResponse.ReviewQuestion rq = new SimulationReviewResponse.ReviewQuestion();
            rq.setNumberLabel(scene.getQuestion().getNumberLabel());
            rq.setText(scene.getQuestion().getText());
            rq.setOptions(scene.getQuestion().getOptions());
            rq.setCorrectAnswer(correctAnswer);
            rq.setUserAnswer(userAnswer);
            rq.setCorrect(correctAnswer.equalsIgnoreCase(userAnswer));
            rs.setQuestion(rq);

            reviewScenes.add(rs);
        }
        response.setScenes(reviewScenes);

        // Build review results
        SimulationReviewResponse.ReviewResults rr = new SimulationReviewResponse.ReviewResults();
        rr.setTotalCorrect(attempt.getScore());
        rr.setTotalQuestions(attempt.getTotalQuestions());
        rr.setPercentage(attempt.getPercentage());
        rr.setPassed(attempt.isPassed());
        if (full.getResults() != null) {
            rr.setScoreRingCircumference(full.getResults().getScoreRingCircumference());
            rr.setBreakdown(full.getResults().getBreakdown());
            String key = String.valueOf(attempt.getScore());
            rr.setTitle(full.getResults().getTitles() != null
                    ? full.getResults().getTitles().getOrDefault(key, "Simulation complete.") : "Simulation complete.");
            rr.setSubtitle(full.getResults().getSubtitles() != null
                    ? full.getResults().getSubtitles().getOrDefault(key, "") : "");
        }
        response.setResults(rr);

        return response;
    }

    // ── Learner: Submit full simulation ──────────────────────────────────

    public SimulationResultResponse submitAnswers(String simId, SimulationSubmitRequest request,
                                                   UUID userId, String tenantSlug) {
        if (attemptRepository.existsByUserIdAndSimId(userId, simId)) {
            throw new IllegalStateException("Simulation already exists as completed. You cannot retake it.");
        }

        SimulationDetailResponse full = getFullSimulation(simId);
        Map<String, String> answers = request.getAnswers();

        int totalCorrect = 0;
        List<SimulationResultResponse.BreakdownEntry> breakdown = new ArrayList<>();

        for (int i = 0; i < full.getScenes().size(); i++) {
            SimulationDetailResponse.Scene scene = full.getScenes().get(i);
            String correctAnswer = scene.getQuestion().getCorrectAnswer();
            String userAnswer = answers != null ? answers.getOrDefault(scene.getId(), "") : "";
            boolean correct = correctAnswer.equalsIgnoreCase(userAnswer);
            if (correct) totalCorrect++;

            SimulationResultResponse.BreakdownEntry entry = new SimulationResultResponse.BreakdownEntry();
            entry.setSceneId(scene.getId());
            entry.setUserAnswer(userAnswer);
            entry.setCorrectAnswer(correctAnswer);
            entry.setCorrect(correct);

            // Attach feedback
            if (correct) {
                entry.setFeedbackTitle(scene.getFeedback().getCorrect().getTitle());
                entry.setFeedbackText(scene.getFeedback().getCorrect().getText());
            } else {
                SimulationDetailResponse.FeedbackEntry wrongEntry =
                        scene.getFeedback().getWrong().get(userAnswer.toUpperCase());
                if (wrongEntry != null) {
                    entry.setFeedbackTitle(wrongEntry.getTitle());
                    entry.setFeedbackText(wrongEntry.getText());
                }
            }

            if (full.getResults() != null && full.getResults().getBreakdown() != null
                    && i < full.getResults().getBreakdown().size()) {
                entry.setLabel(full.getResults().getBreakdown().get(i).getLabel());
            } else {
                entry.setLabel("Scene " + (i + 1));
            }
            breakdown.add(entry);
        }

        int totalQuestions = full.getScenes().size();
        int percentage = totalQuestions > 0 ? (int) Math.round(100.0 * totalCorrect / totalQuestions) : 0;
        boolean passed = percentage >= PASS_THRESHOLD;

        // Store attempt
        SimulationAttempt attempt = new SimulationAttempt();
        attempt.setUserId(userId);
        attempt.setSimId(simId);
        attempt.setTenantSlug(tenantSlug);
        attempt.setAnswers(toJson(answers));
        attempt.setScore(totalCorrect);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setPercentage(percentage);
        attempt.setPassed(passed);
        attemptRepository.save(attempt);

        // Audit log
        auditService.logForTenant(
                userId, AuditAction.SIMULATION_SUBMITTED, "SIMULATION",
                simId, "Simulation submitted: " + full.getTitle()
                        + " | Score: " + totalCorrect + "/" + totalQuestions
                        + " (" + percentage + "%) | " + (passed ? "PASSED" : "FAILED"),
                tenantSlug);

        // Build response
        SimulationResultResponse response = new SimulationResultResponse();
        response.setTotalCorrect(totalCorrect);
        response.setTotalQuestions(totalQuestions);
        response.setPercentage(percentage);
        response.setPassed(passed);
        response.setBreakdown(breakdown);

        String key = String.valueOf(totalCorrect);
        if (full.getResults() != null) {
            response.setTitle(full.getResults().getTitles() != null
                    ? full.getResults().getTitles().getOrDefault(key, "Simulation complete.")
                    : "Simulation complete.");
            response.setSubtitle(full.getResults().getSubtitles() != null
                    ? full.getResults().getSubtitles().getOrDefault(key, "")
                    : "");
        }

        return response;
    }

    // ── Internal: full data (used by platform controller and internally) ─

    public SimulationDetailResponse getFullSimulation(String simId) {
        Simulation sim = repository.findBySimId(simId)
                .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + simId));
        return parseData(sim);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private LearnerSimulationDetailResponse stripSensitiveData(SimulationDetailResponse full) {
        LearnerSimulationDetailResponse stripped = new LearnerSimulationDetailResponse();
        stripped.setId(full.getId());
        stripped.setTitle(full.getTitle());
        stripped.setAccentColor(full.getAccentColor());
        stripped.setBadge(full.getBadge());
        stripped.setTotalDecisions(full.getTotalDecisions());
        stripped.setIntro(full.getIntro());

        List<LearnerSimulationDetailResponse.LearnerScene> scenes = new ArrayList<>();
        for (SimulationDetailResponse.Scene s : full.getScenes()) {
            LearnerSimulationDetailResponse.LearnerScene ls = new LearnerSimulationDetailResponse.LearnerScene();
            ls.setId(s.getId());
            ls.setOrder(s.getOrder());
            ls.setContextBar(s.getContextBar());
            ls.setMockup(s.getMockup());
            ls.setNextButtonText(s.getNextButtonText());

            LearnerSimulationDetailResponse.LearnerQuestion lq = new LearnerSimulationDetailResponse.LearnerQuestion();
            lq.setNumberLabel(s.getQuestion().getNumberLabel());
            lq.setText(s.getQuestion().getText());
            lq.setOptions(s.getQuestion().getOptions());
            ls.setQuestion(lq);

            scenes.add(ls);
        }
        stripped.setScenes(scenes);

        if (full.getResults() != null) {
            LearnerSimulationDetailResponse.LearnerResults lr = new LearnerSimulationDetailResponse.LearnerResults();
            lr.setScoreRingCircumference(full.getResults().getScoreRingCircumference());
            lr.setBreakdown(full.getResults().getBreakdown());
            stripped.setResults(lr);
        }

        return stripped;
    }

    private SimulationDetailResponse parseData(Simulation sim) {
        try {
            SimulationDetailResponse response = objectMapper.readValue(sim.getData(), SimulationDetailResponse.class);
            response.setId(sim.getSimId());
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse simulation data for: " + sim.getSimId(), e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.AnswerRepository;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.QuizResultResponse;
import com.icentric.Icentric.learning.dto.QuizSubmissionRequest;
import com.icentric.Icentric.learning.entity.QuizAnswer;
import com.icentric.Icentric.learning.entity.QuizAttempt;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.QuizAnswerRepository;
import com.icentric.Icentric.learning.repository.QuizAttemptRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class QuizService {

    private final AnswerRepository answerRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;

    // NEW dependencies
    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final CertificateService certificateService;
    private final IssuedCertificateRepository issuedRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public QuizService(
            AnswerRepository answerRepository,
            QuizAttemptRepository attemptRepository,
            QuizAnswerRepository quizAnswerRepository,
            LessonRepository lessonRepository,
            ModuleRepository moduleRepository,
            CertificateService certificateService,
            IssuedCertificateRepository issuedRepository,
            UserAssignmentRepository assignmentRepository,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.certificateService = certificateService;
        this.issuedRepository = issuedRepository;
        this.assignmentRepository = assignmentRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    private static final double PASS_THRESHOLD = 0.7; // 70%
    private static final int MAX_ATTEMPTS = 3;

    public QuizResultResponse submitQuiz(UUID userId, QuizSubmissionRequest request) {

        long attemptCount =
                attemptRepository.countByUserIdAndLessonId(
                        userId,
                        request.lessonId()
                );

        if (attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Max attempts reached");
        }

        UUID attemptId = UUID.randomUUID();
        int correct = 0;

        for (var answer : request.answers()) {

            var correctAnswer =
                    answerRepository.findById(answer.answerId())
                            .orElseThrow();

            boolean isCorrect = correctAnswer.getIsCorrect();

            if (isCorrect) correct++;

            QuizAnswer qa = new QuizAnswer();
            qa.setId(UUID.randomUUID());
            qa.setAttemptId(attemptId);
            qa.setQuestionId(answer.questionId());
            qa.setAnswerId(answer.answerId());
            qa.setIsCorrect(isCorrect);

            quizAnswerRepository.save(qa);
        }

        int total = request.answers().size();

        double scorePercent = total == 0 ? 0 : (correct * 1.0 / total);

        boolean passed = scorePercent >= PASS_THRESHOLD;

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(attemptId);
        attempt.setUserId(userId);
        attempt.setLessonId(request.lessonId());
        attempt.setScore(correct);
        attempt.setTotalQuestions(total);
        attempt.setAttemptNumber((int) attemptCount + 1);
        attempt.setPassed(passed);
        attempt.setAttemptedAt(Instant.now());

        attemptRepository.save(attempt);

        auditService.log(userId, "QUIZ_ATTEMPT", "QUIZ", request.lessonId().toString(), "Quiz attempt " + attempt.getAttemptNumber() + " with score " + scorePercent);

// 🔥 ADD FAILED LOGIC HERE
        if (!passed && attemptCount + 1 >= MAX_ATTEMPTS) {

            var lesson = lessonRepository.findById(request.lessonId())
                    .orElseThrow();

            var module = moduleRepository.findById(lesson.getModuleId())
                    .orElseThrow();

            UUID trackId = module.getTrackId();

            var assignment = assignmentRepository
                    .findByUserIdAndTrackId(userId, trackId)
                    .orElseThrow();

            assignment.setStatus(AssignmentStatus.FAILED);
            assignmentRepository.save(assignment);

            notificationService.createNotification(
                    userId,
                    "FAILED",
                    "You have failed the training after maximum attempts."
            );
        }


        // 🔥 Only trigger certificate if PASSED
        if (passed) {

            var lesson = lessonRepository.findById(request.lessonId())
                    .orElseThrow();

            var module = moduleRepository.findById(lesson.getModuleId())
                    .orElseThrow();

            UUID trackId = module.getTrackId();

            certificateService.checkAndIssue(userId, trackId);
        }

        int attemptNumber = (int) attemptCount + 1;
        int remainingAttempts = Math.max(0, MAX_ATTEMPTS - attemptNumber);

        return new QuizResultResponse(
                correct,
                total,
                passed,
                attemptNumber,
                remainingAttempts
        );
    }
}

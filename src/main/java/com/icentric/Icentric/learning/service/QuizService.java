package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.AnswerRepository;
import com.icentric.Icentric.learning.dto.QuizSubmissionRequest;
import com.icentric.Icentric.learning.entity.QuizAnswer;
import com.icentric.Icentric.learning.entity.QuizAttempt;
import com.icentric.Icentric.learning.repository.QuizAnswerRepository;
import com.icentric.Icentric.learning.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class QuizService {

    private final AnswerRepository answerRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;

    public QuizService(
            AnswerRepository answerRepository,
            QuizAttemptRepository attemptRepository,
            QuizAnswerRepository quizAnswerRepository
    ) {
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.quizAnswerRepository = quizAnswerRepository;
    }

    public int submitQuiz(UUID userId, QuizSubmissionRequest request) {

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

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(attemptId);
        attempt.setUserId(userId);
        attempt.setLessonId(request.lessonId());
        attempt.setScore(correct);
        attempt.setTotalQuestions(request.answers().size());
        attempt.setAttemptedAt(Instant.now());

        attemptRepository.save(attempt);

        return correct;
    }
}
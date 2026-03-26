package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.entity.Answer;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.AnswerRepository;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.constants.NotificationType;
import com.icentric.Icentric.learning.dto.QuizResultResponse;
import com.icentric.Icentric.learning.dto.QuizSubmissionRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.QuizAnswerRepository;
import com.icentric.Icentric.learning.repository.QuizAttemptRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icentric.Icentric.audit.service.AuditService;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    AnswerRepository answerRepository;
    @Mock
    QuizAttemptRepository attemptRepository;
    @Mock
    QuizAnswerRepository quizAnswerRepository;
    @Mock
    LessonRepository lessonRepository;
    @Mock
    ModuleRepository moduleRepository;
    @Mock
    CertificateService certificateService;
    @Mock
    IssuedCertificateRepository issuedRepository;
    @Mock
    UserAssignmentRepository assignmentRepository;
    @Mock
    NotificationService notificationService;
    @Mock
    AuditService auditService;

    @InjectMocks
    QuizService quizService;

    @Test
    @DisplayName("submitQuiz: passing score triggers certificate check")
    void submitQuiz_passScenario() {
        UUID userId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        QuizSubmissionRequest request = new QuizSubmissionRequest(
                lessonId,
                List.of(new QuizSubmissionRequest.AnswerSubmission(questionId, answerId))
        );

        Answer correctAnswer = new Answer();
        correctAnswer.setId(answerId);
        correctAnswer.setQuestionId(questionId);
        correctAnswer.setIsCorrect(true);

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setModuleId(moduleId);

        CourseModule module = new CourseModule();
        module.setId(moduleId);
        module.setTrackId(trackId);

        when(attemptRepository.countByUserIdAndLessonId(userId, lessonId)).thenReturn(0L);
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(correctAnswer));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        QuizResultResponse response = quizService.submitQuiz(userId, request);

        assertThat(response.passed()).isTrue();
        assertThat(response.score()).isEqualTo(1);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.remainingAttempts()).isEqualTo(2);

        verify(certificateService).checkAndIssue(userId, trackId);
        verify(assignmentRepository, never()).findByUserIdAndTrackId(any(), any());
    }

    @Test
    @DisplayName("submitQuiz: failing on final attempt marks assignment FAILED")
    void submitQuiz_failureScenario() {
        UUID userId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        QuizSubmissionRequest request = new QuizSubmissionRequest(
                lessonId,
                List.of(new QuizSubmissionRequest.AnswerSubmission(questionId, answerId))
        );

        Answer wrongAnswer = new Answer();
        wrongAnswer.setId(answerId);
        wrongAnswer.setQuestionId(questionId);
        wrongAnswer.setIsCorrect(false);

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setModuleId(moduleId);

        CourseModule module = new CourseModule();
        module.setId(moduleId);
        module.setTrackId(trackId);

        UserAssignment assignment = new UserAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(userId);
        assignment.setTrackId(trackId);
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);

        when(attemptRepository.countByUserIdAndLessonId(userId, lessonId)).thenReturn(2L);
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(wrongAnswer));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(assignmentRepository.findByUserIdAndTrackId(userId, trackId)).thenReturn(Optional.of(assignment));

        QuizResultResponse response = quizService.submitQuiz(userId, request);

        assertThat(response.passed()).isFalse();
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.attemptNumber()).isEqualTo(3);
        assertThat(response.remainingAttempts()).isEqualTo(0);

        ArgumentCaptor<UserAssignment> assignmentCaptor = ArgumentCaptor.forClass(UserAssignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getStatus()).isEqualTo(AssignmentStatus.FAILED);

        verify(notificationService).createNotification(
                userId,
                NotificationType.FAILED,
                "You have failed the training after maximum attempts."
        );
        verify(certificateService, never()).checkAndIssue(any(), any());
    }

    @Test
    @DisplayName("submitQuiz: throws when max attempts already reached")
    void submitQuiz_maxAttemptsReached() {
        UUID userId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        QuizSubmissionRequest request = new QuizSubmissionRequest(
                lessonId,
                List.of(new QuizSubmissionRequest.AnswerSubmission(questionId, answerId))
        );

        when(attemptRepository.countByUserIdAndLessonId(userId, lessonId)).thenReturn(3L);

        assertThatThrownBy(() -> quizService.submitQuiz(userId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Max attempts reached");

        verify(answerRepository, never()).findById(any());
        verify(certificateService, never()).checkAndIssue(any(), any());
    }
}

package com.icentric.Icentric.content.service;


import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.dto.CreateQuestionRequest;
import com.icentric.Icentric.content.dto.QuestionResponse;
import com.icentric.Icentric.content.entity.Answer;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.entity.Question;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.QuestionRepository;
import com.icentric.Icentric.content.repository.AnswerRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final TrackRepository trackRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public QuestionService(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            LessonRepository lessonRepository,
            ModuleRepository moduleRepository,
            TrackRepository trackRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    public List<QuestionResponse> getQuestions(UUID lessonId) {

        List<Question> questions =
                questionRepository.findByLessonId(lessonId);

        return questions.stream().map(q -> {

            var answers = answerRepository
                    .findByQuestionId(q.getId())
                    .stream()
                    .map(a -> new QuestionResponse.AnswerOption(
                            a.getId(),
                            a.getAnswerText()
                    ))
                    .toList();

            return new QuestionResponse(
                    q.getId(),
                    q.getQuestionText(),
                    q.getQuestionType(),
                    answers
            );

        }).toList();
    }
    public void createQuestion(CreateQuestionRequest request) {
        assertTrackEditable(request.lessonId());

        UUID questionId = UUID.randomUUID();

        Question question = new Question();
        question.setId(questionId);
        question.setLessonId(request.lessonId());
        question.setQuestionText(request.questionText());
        question.setQuestionType(request.questionType());
        question.setCreatedAt(Instant.now());

        questionRepository.save(question);

        for (var answer : request.answers()) {

            Answer a = new Answer();
            a.setId(UUID.randomUUID());
            a.setQuestionId(questionId);
            a.setAnswerText(answer.answerText());
            a.setIsCorrect(answer.isCorrect());

            answerRepository.save(a);
        }

        logCreateQuestion(question);
    }

    private void logCreateQuestion(Question question) {
        UUID actorId = currentActorUserId();
        if (actorId == null) {
            return;
        }
        Lesson lesson = lessonRepository.findById(question.getLessonId()).orElse(null);
        String lessonLabel = lesson != null
                ? "'" + lesson.getTitle() + "' [" + lesson.getId() + "]"
                : "lesson " + question.getLessonId();
        String trackLabel = "unknown track";
        if (lesson != null) {
            trackLabel = moduleRepository.findById(lesson.getModuleId())
                    .map(module -> auditMetadataService.describeTrack(module.getTrackId()))
                    .orElse("unknown track");
        }

        auditService.log(
                actorId,
                AuditAction.CREATE_QUESTION,
                "QUESTION",
                question.getId().toString(),
                auditMetadataService.describeUser(actorId)
                        + " created question [" + question.getId() + "] for "
                        + lessonLabel + " in " + trackLabel
        );
    }

    private UUID currentActorUserId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        return userIdRaw == null ? null : UUID.fromString(userIdRaw.toString());
    }

    private void assertTrackEditable(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + lessonId));
        CourseModule module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new NoSuchElementException("Module not found: " + lesson.getModuleId()));
        Track track = trackRepository.findById(module.getTrackId())
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + module.getTrackId()));
        if ("PUBLISHED".equals(track.getStatus())) {
            throw new IllegalStateException("Cannot modify a published track version. Create a new version instead.");
        }
    }
}

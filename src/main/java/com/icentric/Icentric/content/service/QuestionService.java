package com.icentric.Icentric.content.service;


import com.icentric.Icentric.content.dto.CreateQuestionRequest;
import com.icentric.Icentric.content.dto.QuestionResponse;
import com.icentric.Icentric.content.entity.Answer;
import com.icentric.Icentric.content.entity.Question;
import com.icentric.Icentric.content.repository.QuestionRepository;
import com.icentric.Icentric.content.repository.AnswerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public QuestionService(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
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
    }
}

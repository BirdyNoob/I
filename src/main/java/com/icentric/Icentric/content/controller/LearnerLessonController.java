package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.LessonDetailResponse;
import com.icentric.Icentric.content.service.LessonService;
import com.icentric.Icentric.learning.dto.QuizSubmissionRequest;
import com.icentric.Icentric.learning.service.QuizService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
public class LearnerLessonController {

    private final LessonService service;
    private final QuizService quizService;

    public LearnerLessonController(LessonService service, QuizService quizService) {
        this.quizService = quizService;
        this.service = service;
    }

    @GetMapping("/{lessonId}")
    public LessonDetailResponse getLesson(
            @PathVariable UUID lessonId
    ) {
        return service.getLesson(lessonId);
    }
    @PostMapping("/{lessonId}/quiz-attempt")
    public Map<String, Integer> submitQuiz(

            @PathVariable UUID lessonId,
            @RequestBody QuizSubmissionRequest request,
            Authentication authentication
    ) {
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            throw new IllegalArgumentException("Missing userId in authentication token");
        }
        UUID userId = UUID.fromString(userIdRaw.toString());

        int score = quizService.submitQuiz(userId, request);

        return Map.of("score", score);
    }
}

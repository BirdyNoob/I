package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateQuestionRequest;
import com.icentric.Icentric.content.service.QuestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/content")
public class PlatformQuestionsController {
    private final QuestionService questionService;

    public PlatformQuestionsController(QuestionService questionService) {
        this.questionService = questionService;
    }
    @PostMapping("/questions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void createQuestion(
            @RequestBody CreateQuestionRequest request
    ) {
        questionService.createQuestion(request);
    }
}

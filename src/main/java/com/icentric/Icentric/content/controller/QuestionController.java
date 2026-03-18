package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.QuestionResponse;
import com.icentric.Icentric.content.service.QuestionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
public class QuestionController {

    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @GetMapping("/{lessonId}/questions")
    public List<QuestionResponse> questions(
            @PathVariable UUID lessonId
    ) {
        return service.getQuestions(lessonId);
    }
}

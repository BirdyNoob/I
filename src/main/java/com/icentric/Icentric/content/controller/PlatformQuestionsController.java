package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateQuestionRequest;
import com.icentric.Icentric.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/content")
@Tag(name = "Platform Questions", description = "APIs for platform admins to manage global/platform-level questions")
public class PlatformQuestionsController {
    private final QuestionService questionService;

    public PlatformQuestionsController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "Create a new question", description = "Creates a new question at the platform level.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully created the question"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @PostMapping("/questions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void createQuestion(
            @Valid @RequestBody CreateQuestionRequest request
    ) {
        questionService.createQuestion(request);
    }
}

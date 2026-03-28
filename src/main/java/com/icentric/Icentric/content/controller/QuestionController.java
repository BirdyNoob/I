package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.QuestionResponse;
import com.icentric.Icentric.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
@Tag(name = "Questions", description = "APIs for interacting with lesson questions")
public class QuestionController {

    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @Operation(summary = "Get questions for a lesson", description = "Retrieves all questions associated with a specific lesson.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved questions"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Lesson not found")
    })
    @GetMapping("/{lessonId}/questions")
    public List<QuestionResponse> questions(
            @Parameter(description = "UUID of the lesson") @PathVariable UUID lessonId
    ) {
        return service.getQuestions(lessonId);
    }
}

package com.icentric.Icentric.content.controller;
import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.service.LessonService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/content/modules")
public class LessonController {

    private final LessonService service;

    public LessonController(LessonService service) {
        this.service = service;
    }

    @PostMapping("/{moduleId}/lessons")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Lesson createLesson(
            @PathVariable UUID moduleId,
            @RequestBody CreateLessonRequest request
    ) {
        return service.createLesson(moduleId, request);
    }
}
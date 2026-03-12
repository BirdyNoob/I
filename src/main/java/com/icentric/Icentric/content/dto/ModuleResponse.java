package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

public record ModuleResponse(
        UUID id,
        String title,
        List<LessonResponse> lessons
) {}

package com.icentric.Icentric.content.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTrackRequest(
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "title must not start or end with whitespace")
        @Size(max = 255)
        String title,
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "description must not start or end with whitespace")
        @Size(max = 2000)
        String description
) {}

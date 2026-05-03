package com.icentric.Icentric.content.dto;

import com.icentric.Icentric.common.enums.Department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTrackRequest(

        @NotBlank
        @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase letters, digits, or hyphens")
        String slug,
        @NotBlank
        @Size(max = 255)
        String title,
        @NotBlank
        @Size(max = 2000)
        String description,
        @NotNull
        Department department,
        @NotBlank
        @Size(max = 50)
        String trackType,
        @NotNull
        @Positive
        Integer estimatedMins

) {}

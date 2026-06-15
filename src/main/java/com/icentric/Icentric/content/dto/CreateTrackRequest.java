package com.icentric.Icentric.content.dto;

import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.content.constants.CourseType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTrackRequest(
        @NotBlank
        @Size(max = 255)
        String title,
        @NotBlank
        @Size(max = 2000)
        String description,
        @NotNull
        Department department,
        @NotNull
        CourseType courseType,
        @NotNull
        @Positive
        Integer estimatedMins,
        @NotNull
        Boolean isMandatory

) {}

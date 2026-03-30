package com.icentric.Icentric.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReminderConfigRequest(
        @NotNull
        Boolean reminderEnabled,
        @NotEmpty
        List<@NotNull @Min(1) Integer> reminderOffsetsHours,
        @NotNull
        Boolean escalationEnabled,
        @NotNull
        @Min(0)
        Integer escalationDelayHours
) {}

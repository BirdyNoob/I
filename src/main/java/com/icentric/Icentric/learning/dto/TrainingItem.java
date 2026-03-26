package com.icentric.Icentric.learning.dto;

import com.icentric.Icentric.learning.constants.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record TrainingItem(

        UUID trackId,
        String trackTitle,
        AssignmentStatus status,
        Instant dueDate,
        Long daysLeft,
        int progressPercent

) {}

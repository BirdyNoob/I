package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record TrainingItem(

        UUID trackId,
        String trackTitle,
        String status,
        Instant dueDate,
        Long daysLeft,
        int progressPercent

) {}

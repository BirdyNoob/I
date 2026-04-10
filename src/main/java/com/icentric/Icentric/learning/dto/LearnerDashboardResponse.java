package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.List;

public record LearnerDashboardResponse(
        String learnerName,
        int learningStreakDays,
        Instant nextDeadline,

        List<TrainingItem> trainings,
        List<CertificateItem> certificates

) {}

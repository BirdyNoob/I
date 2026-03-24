package com.icentric.Icentric.learning.dto;

import java.util.List;

public record LearnerDashboardResponse(

        List<TrainingItem> trainings,
        List<CertificateItem> certificates

) {}

package com.icentric.Icentric.learning.dto;


import java.util.List;

public record AdminOverviewResponse(
        Kpis kpis,
        List<CompletionByDepartment> completionByDepartment,
        RiskMaturity riskMaturity,
        List<OverdueUser> overdueUsers,
        List<ActivityItem> activityFeed,
        List<QuizPerformanceByDepartment> quizPerformanceByDepartment,
        List<HighestFailureLesson> highestFailureLessons
) {
    public record Kpis(
            double overallCompletionPercent,
            double overallCompletionDeltaPercent,
            ActiveLearners activeLearners,
            OverdueSummary overdueUsers,
            double avgAssessmentScorePercent,
            double avgAssessmentTrendPoints,
            long certificatesIssuedThisMonth
    ) {}

    public record ActiveLearners(long active, long total) {}

    public record OverdueSummary(long total, long newThisWeek) {}

    public record CompletionByDepartment(
            String department,
            double progressPercent,
            Status status
    ) {
        public enum Status {
            ON_TRACK,
            AT_RISK,
            CRITICAL
        }
    }

    public record RiskMaturity(
            List<String> labels,
            List<Double> currentScores,
            List<Double> targetScores,
            double currentAverage,
            double targetAverage
    ) {}

    public record OverdueUser(
            String name,
            String department,
            long daysOverdue
    ) {}

    public record ActivityItem(
            Type type,
            String text,
            String timeAgo
    ) {
        public enum Type {
            SUCCESS,
            INFO,
            WARNING,
            ERROR
        }
    }

    public record QuizPerformanceByDepartment(
            String department,
            double avgScorePercent,
            double passRatePercent
    ) {}

    public record HighestFailureLesson(
            String lessonTitle,
            String department,
            double failRatePercent
    ) {}
}

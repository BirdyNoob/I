package com.icentric.Icentric.platform.dto;

import java.util.List;

public record CrossTenantAnalyticsResponse(
        Kpis kpis,
        Charts charts,
        List<TenantComparison> tenantComparison,
        List<RiskHeatmapItem> riskHeatmap,
        List<FailingScenario> failingScenarios,
        ContentImpact contentImpact
) {
    public record Kpis(
            KpiMetric activeLearners,
            KpiMetric completionRate,
            KpiMetric certsIssued,
            KpiMetric mau,
            KpiMetric avgPassRate,
            KpiMetric newTenants
    ) {}

    public record KpiMetric(
            long value,
            String trend,
            String status
    ) {}

    public record Charts(
            CompletionTrend completionTrend,
            List<TrackPerformanceItem> trackPerformance,
            AssessmentScatter assessmentScatter
    ) {}

    public record CompletionTrend(
            List<String> labels,
            List<Integer> platformAvg,
            List<List<Integer>> tenantBenchmarks
    ) {}

    public record TrackPerformanceItem(
            String track,
            int percentage
    ) {}

    public record AssessmentScatter(
            List<ScatterPoint> standard,
            List<ScatterPoint> needsReview
    ) {}

    public record ScatterPoint(
            double x,
            double y
    ) {}

    public record TenantComparison(
            String name,
            String anonName,
            String plan,
            long users,
            int completion,
            int avgScore,
            int riskScore,
            String riskLevel,
            long certs,
            List<Integer> sparkline
    ) {}

    public record RiskHeatmapItem(
            String dept,
            int promptSafety,
            int dataPrivacy,
            int policyComp
    ) {}

    public record FailingScenario(
            String title,
            String track,
            String module,
            int failRate
    ) {}

    public record ContentImpact(
            List<Integer> trend,
            List<ContentEvent> events
    ) {}

    public record ContentEvent(
            String date,
            String title,
            String impact,
            ChartPoint chartPoint
    ) {}

    public record ChartPoint(
            String x,
            int y
    ) {}
}

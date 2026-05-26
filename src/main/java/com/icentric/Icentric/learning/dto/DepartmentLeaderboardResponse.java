package com.icentric.Icentric.learning.dto;

import java.util.List;

public record DepartmentLeaderboardResponse(
        List<LeaderboardRow> rankings
) {
    public record LeaderboardRow(
            int rank,
            String departmentDisplayName,
            double leaderboardScore,
            double completionRatePercent,
            double averageQuizScorePercent,
            long completedAssignments,
            long totalAssignments,
            String activeStatus // "LEADER", "ON_TRACK", "FALLING_BEHIND"
    ) {}
}

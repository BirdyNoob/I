package com.icentric.Icentric.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class LeaderboardDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardResponse {
        private int userCurrentRank;
        private int userCurrentXp;
        private int currentStreak;
        private int longestStreak;
        private boolean anonymousMode;
        private boolean optIn;
        private List<LeaderboardRow> rankings;
        private int totalPages;
        private long totalElements;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardRow {
        private int rank;
        private String displayName;
        private String department;
        private int totalXp;
        private int streak;
        private boolean isCurrentUser;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacyUpdateRequest {
        private boolean leaderboardOptIn;
        private boolean anonymousMode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrantXpRequest {
        private String userId;
        private int xpAmount;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrantXpResponse {
        private String userId;
        private int newTotalXp;
        private String transactionId;
    }
}

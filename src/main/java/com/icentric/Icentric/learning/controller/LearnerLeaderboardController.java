package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.common.security.SecurityUtils;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.LeaderboardResponse;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.PrivacyUpdateRequest;
import com.icentric.Icentric.learning.entity.LearnerStats;
import com.icentric.Icentric.learning.repository.LearnerStatsRepository;
import com.icentric.Icentric.learning.service.XpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learner/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Leaderboard (Learner)", description = "APIs for learners to interact with the gamified leaderboard and privacy preferences")
public class LearnerLeaderboardController {

    private final XpService xpService;
    private final LearnerStatsRepository statsRepository;

    @Operation(summary = "Retrieve Leaderboard View", description = "Fetches a page of leaderboard rankings respecting privacy options alongside user's current HUD statistics.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leaderboard view"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Learner role required")
    })
    @GetMapping
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID userId = SecurityUtils.currentUserId();
        LeaderboardResponse response = xpService.getLeaderboardView(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update Leaderboard Privacy Settings", description = "Allows the logged-in learner to toggle leaderboard opt-in or anonymity masking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully updated privacy settings"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Learner role required")
    })
    @PatchMapping("/privacy")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<Map<String, Object>> updatePrivacy(
            @Valid @RequestBody PrivacyUpdateRequest request
    ) {
        UUID userId = SecurityUtils.currentUserId();
        LearnerStats stats = xpService.updatePrivacySettings(userId, request);

        int rank = 0;
        if (stats.getLeaderboardOptIn()) {
            rank = (int) statsRepository.countByLeaderboardOptInTrueAndTotalXpGreaterThan(stats.getTotalXp()) + 1;
        }

        Map<String, Object> response = Map.of(
                "userCurrentRank", rank,
                "anonymousMode", stats.getAnonymousMode(),
                "optIn", stats.getLeaderboardOptIn(),
                "anonymousAlias", stats.getAnonymousAlias() != null ? stats.getAnonymousAlias() : ""
        );

        return ResponseEntity.ok(response);
    }
}

package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.LeaderboardDtos.GrantXpRequest;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.GrantXpResponse;
import com.icentric.Icentric.learning.service.XpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Leaderboard (Admin)", description = "APIs for platform administrators to override XP rankings and manually award points")
public class AdminLeaderboardController {

    private final XpService xpService;

    @Operation(summary = "Grant Manual/Override XP", description = "Allows administrators to manually award XP points to a specific learner, writing a transaction ledger entry.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully granted manual XP override"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    @PostMapping("/grant-xp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrantXpResponse> grantXp(
            @Valid @RequestBody GrantXpRequest request
    ) {
        UUID userUuid = UUID.fromString(request.getUserId());
        GrantXpResponse response = xpService.grantManualXp(userUuid, request.getXpAmount(), request.getReason());
        return ResponseEntity.ok(response);
    }
}

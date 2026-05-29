package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.common.security.SecurityUtils;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.GrantXpRequest;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.GrantXpResponse;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.LeaderboardResponse;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.PrivacyUpdateRequest;
import com.icentric.Icentric.learning.entity.LearnerStats;
import com.icentric.Icentric.learning.repository.LearnerStatsRepository;
import com.icentric.Icentric.learning.service.XpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock private XpService xpService;
    @Mock private LearnerStatsRepository statsRepository;

    private LearnerLeaderboardController learnerController;
    private AdminLeaderboardController adminController;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private UUID userId;

    @BeforeEach
    void setUp() {
        learnerController = new LearnerLeaderboardController(xpService, statsRepository);
        adminController = new AdminLeaderboardController(xpService);
        userId = UUID.randomUUID();

        // Mock static SecurityUtils
        mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::currentUserId).thenReturn(userId);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("Learner GET /leaderboard delegates to service")
    void getLeaderboard_delegatesToXpService() {
        LeaderboardResponse mockResponse = LeaderboardResponse.builder()
                .userCurrentRank(10)
                .userCurrentXp(500)
                .build();

        when(xpService.getLeaderboardView(userId, PageRequest.of(0, 10))).thenReturn(mockResponse);

        ResponseEntity<LeaderboardResponse> response = learnerController.getLeaderboard(0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockResponse);
    }

    @Test
    @DisplayName("Learner PATCH /leaderboard/privacy updates preferences and returns calculated rank map")
    void updatePrivacy_savesSettingsAndReturnsMetadataMap() {
        PrivacyUpdateRequest request = new PrivacyUpdateRequest(true, true);
        LearnerStats stats = new LearnerStats();
        stats.setUserId(userId);
        stats.setTotalXp(120);
        stats.setLeaderboardOptIn(true);
        stats.setAnonymousMode(true);
        stats.setAnonymousAlias("Anonymous Learner #12");

        when(xpService.updatePrivacySettings(userId, request)).thenReturn(stats);
        when(statsRepository.countByLeaderboardOptInTrueAndTotalXpGreaterThan(120)).thenReturn(3L);

        ResponseEntity<Map<String, Object>> response = learnerController.updatePrivacy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("userCurrentRank")).isEqualTo(4); // 3 users above + 1
        assertThat(body.get("anonymousMode")).isEqualTo(true);
        assertThat(body.get("optIn")).isEqualTo(true);
        assertThat(body.get("anonymousAlias")).isEqualTo("Anonymous Learner #12");
    }

    @Test
    @DisplayName("Admin POST /grant-xp awards override XP successfully")
    void grantXp_awardsOverrideXpSuccessfully() {
        GrantXpRequest request = new GrantXpRequest(userId.toString(), 150, "Extraordinary contribution");
        GrantXpResponse mockResponse = GrantXpResponse.builder()
                .userId(userId.toString())
                .newTotalXp(1000)
                .transactionId(UUID.randomUUID().toString())
                .build();

        when(xpService.grantManualXp(userId, 150, "Extraordinary contribution")).thenReturn(mockResponse);

        ResponseEntity<GrantXpResponse> response = adminController.grantXp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockResponse);
    }
}

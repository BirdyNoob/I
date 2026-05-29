package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.LeaderboardResponse;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.LeaderboardRow;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.PrivacyUpdateRequest;
import com.icentric.Icentric.learning.entity.LearnerStats;
import com.icentric.Icentric.learning.entity.XpTransactionLog;
import com.icentric.Icentric.learning.repository.LearnerStatsRepository;
import com.icentric.Icentric.learning.repository.XpTransactionLogRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpServiceTest {

    @Mock LearnerStatsRepository statsRepository;
    @Mock XpTransactionLogRepository transactionRepository;
    @Mock TenantSchemaService tenantSchemaService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserRepository userRepository;
    @Mock TenantUserRepository tenantUserRepository;
    @Mock TenantRepository tenantRepository;

    @InjectMocks
    XpService xpService;

    private UUID userId;
    private UUID refId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        refId = UUID.randomUUID();
        TenantContext.setTenant("acme");
    }

    @Test
    @DisplayName("processXpAllocation should skip point award if idempotency check returns true")
    void processXpAllocation_skipsOnIdempotencyMatch() {
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "LESSON_COMPLETED", refId))
                .thenReturn(true);

        xpService.processXpAllocation(userId, "LESSON_COMPLETED", "LESSON", refId, 0, false);

        verify(statsRepository, never()).findById(any(UUID.class));
        verify(transactionRepository, never()).save(any(XpTransactionLog.class));
    }

    @Test
    @DisplayName("processXpAllocation should award 20 XP for standard lesson completions and start streak")
    void processXpAllocation_awardsStandardLessonXp() {
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "LESSON_COMPLETED", refId))
                .thenReturn(false);
        when(statsRepository.findById(userId)).thenReturn(Optional.empty());

        xpService.processXpAllocation(userId, "LESSON_COMPLETED", "LESSON", refId, 0, false);

        ArgumentCaptor<LearnerStats> statsCaptor = ArgumentCaptor.forClass(LearnerStats.class);
        verify(statsRepository, times(2)).save(statsCaptor.capture());
        LearnerStats capturedStats = statsCaptor.getAllValues().get(1);

        // 20 XP base + 10 XP daily streak refill
        assertThat(capturedStats.getTotalXp()).isEqualTo(30);
        assertThat(capturedStats.getCurrentStreak()).isEqualTo(1);
        assertThat(capturedStats.getLastActiveDate()).isEqualTo(LocalDate.now(ZoneOffset.UTC));

        verify(transactionRepository, times(2)).save(any(XpTransactionLog.class));
    }

    @Test
    @DisplayName("processXpAllocation should award 24 XP (1.2x) for lesson completed when streak is >= 7 days")
    void processXpAllocation_appliesStreakMultiplier() {
        LearnerStats existingStats = new LearnerStats();
        existingStats.setUserId(userId);
        existingStats.setTotalXp(500);
        existingStats.setCurrentStreak(7);
        existingStats.setLongestStreak(7);
        existingStats.setLastActiveDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        existingStats.setLeaderboardOptIn(true);
        existingStats.setAnonymousMode(false);

        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "LESSON_COMPLETED", refId))
                .thenReturn(false);
        when(statsRepository.findById(userId)).thenReturn(Optional.of(existingStats));

        xpService.processXpAllocation(userId, "LESSON_COMPLETED", "LESSON", refId, 0, false);

        ArgumentCaptor<LearnerStats> statsCaptor = ArgumentCaptor.forClass(LearnerStats.class);
        verify(statsRepository, times(2)).save(statsCaptor.capture());
        LearnerStats capturedStats = statsCaptor.getAllValues().get(1);

        // Previous: 500 XP + 24 XP (20 * 1.2x) + 10 XP (daily streak) = 534 XP
        assertThat(capturedStats.getTotalXp()).isEqualTo(534);
        assertThat(capturedStats.getCurrentStreak()).isEqualTo(8);
    }

    @Test
    @DisplayName("processXpAllocation should award 150 XP for course completion")
    void processXpAllocation_awardsCourseCompletedXp() {
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "COURSE_COMPLETED", refId))
                .thenReturn(false);
        when(statsRepository.findById(userId)).thenReturn(Optional.empty());

        xpService.processXpAllocation(userId, "COURSE_COMPLETED", "TRACK", refId, 0, false);

        ArgumentCaptor<LearnerStats> statsCaptor = ArgumentCaptor.forClass(LearnerStats.class);
        verify(statsRepository).save(statsCaptor.capture());
        LearnerStats capturedStats = statsCaptor.getValue();

        assertThat(capturedStats.getTotalXp()).isEqualTo(150);
    }

    @Test
    @DisplayName("processXpAllocation should award 100 XP for quiz passed on first attempt")
    void processXpAllocation_awardsQuizPassedFirstAttempt() {
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "QUIZ_PASSED", refId))
                .thenReturn(false);
        when(statsRepository.findById(userId)).thenReturn(Optional.empty());

        xpService.processXpAllocation(userId, "QUIZ_PASSED", "ASSESSMENT", refId, 85, true);

        ArgumentCaptor<LearnerStats> statsCaptor = ArgumentCaptor.forClass(LearnerStats.class);
        verify(statsRepository).save(statsCaptor.capture());
        LearnerStats capturedStats = statsCaptor.getValue();

        assertThat(capturedStats.getTotalXp()).isEqualTo(100);
    }

    @Test
    @DisplayName("processXpAllocation should award 40 XP for quiz passed on retake")
    void processXpAllocation_awardsQuizPassedRetake() {
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "QUIZ_PASSED", refId))
                .thenReturn(false);
        when(statsRepository.findById(userId)).thenReturn(Optional.empty());

        xpService.processXpAllocation(userId, "QUIZ_PASSED", "ASSESSMENT", refId, 85, false);

        ArgumentCaptor<LearnerStats> statsCaptor = ArgumentCaptor.forClass(LearnerStats.class);
        verify(statsRepository).save(statsCaptor.capture());
        LearnerStats capturedStats = statsCaptor.getValue();

        assertThat(capturedStats.getTotalXp()).isEqualTo(40);
    }

    @Test
    @DisplayName("processXpAllocation should award perfect score bonus of 30 XP if score is 100")
    void processXpAllocation_awardsPerfectScoreBonus() {
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "QUIZ_PASSED", refId))
                .thenReturn(false);
        when(transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "PERFECT_SCORE_BONUS", refId))
                .thenReturn(false);
        when(statsRepository.findById(userId)).thenReturn(Optional.empty());

        xpService.processXpAllocation(userId, "QUIZ_PASSED", "ASSESSMENT", refId, 100, true);

        ArgumentCaptor<LearnerStats> statsCaptor = ArgumentCaptor.forClass(LearnerStats.class);
        verify(statsRepository, times(2)).save(statsCaptor.capture());
        
        // Final total XP should be 100 (first attempt quiz pass) + 30 (perfect score) = 130 XP
        LearnerStats capturedStats = statsCaptor.getAllValues().get(1);
        assertThat(capturedStats.getTotalXp()).isEqualTo(130);
    }

    @Test
    @DisplayName("getLeaderboardView should resolve display name and department in batch and correctly mask anonymous users")
    void getLeaderboardView_resolvesNamesAndDepartmentsAndMasksAnonymity() {
        LearnerStats myStats = new LearnerStats();
        myStats.setUserId(userId);
        myStats.setTotalXp(250);
        myStats.setLeaderboardOptIn(true);
        myStats.setAnonymousMode(false);

        UUID anonUserId = UUID.randomUUID();
        LearnerStats anonStats = new LearnerStats();
        anonStats.setUserId(anonUserId);
        anonStats.setTotalXp(300);
        anonStats.setLeaderboardOptIn(true);
        anonStats.setAnonymousMode(true);
        anonStats.setAnonymousAlias("Anonymous Agent #99");

        Pageable pageable = PageRequest.of(0, 10);
        Page<LearnerStats> page = new PageImpl<>(List.of(anonStats, myStats), pageable, 2);

        when(statsRepository.findById(userId)).thenReturn(Optional.of(myStats));
        when(statsRepository.findByLeaderboardOptInTrueOrderByTotalXpDescUserIdAsc(pageable)).thenReturn(page);
        when(statsRepository.countByLeaderboardOptInTrueAndTotalXpGreaterThan(250)).thenReturn(1L);

        User me = new User();
        me.setId(userId);
        me.setName("John Doe");

        User anonymousUser = new User();
        anonymousUser.setId(anonUserId);
        anonymousUser.setName("Jane Smith"); // should be masked in rankings!

        when(userRepository.findAllById(any())).thenReturn(List.of(me, anonymousUser));

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setSlug("acme");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        TenantUser myTu = new TenantUser();
        myTu.setUserId(userId);
        myTu.setDepartment(com.icentric.Icentric.common.enums.Department.ENGINEERING);

        when(tenantUserRepository.findByTenantIdAndUserIdIn(eq(tenant.getId()), any()))
                .thenReturn(List.of(myTu));

        LeaderboardResponse response = xpService.getLeaderboardView(userId, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getUserCurrentRank()).isEqualTo(2); // 1 user greater + 1
        assertThat(response.getRankings()).hasSize(2);

        LeaderboardRow firstRow = response.getRankings().get(0);
        assertThat(firstRow.getRank()).isEqualTo(1);
        assertThat(firstRow.getDisplayName()).isEqualTo("Anonymous Agent #99"); // masked!
        assertThat(firstRow.getDepartment()).isEqualTo("UNKNOWN"); // masked!
        assertThat(firstRow.isCurrentUser()).isFalse();

        LeaderboardRow secondRow = response.getRankings().get(1);
        assertThat(secondRow.getRank()).isEqualTo(2);
        assertThat(secondRow.getDisplayName()).isEqualTo("John Doe"); // unmasked
        assertThat(secondRow.getDepartment()).isEqualTo("Engineering"); // resolved
        assertThat(secondRow.isCurrentUser()).isTrue();
    }

    @Test
    @DisplayName("updatePrivacySettings updates stats preferences successfully")
    void updatePrivacySettings_savesUpdatedPreferences() {
        LearnerStats existingStats = new LearnerStats();
        existingStats.setUserId(userId);
        existingStats.setLeaderboardOptIn(false);
        existingStats.setAnonymousMode(false);

        when(statsRepository.findById(userId)).thenReturn(Optional.of(existingStats));
        when(statsRepository.save(any(LearnerStats.class))).thenAnswer(i -> i.getArgument(0));

        PrivacyUpdateRequest req = new PrivacyUpdateRequest(true, true);
        LearnerStats updated = xpService.updatePrivacySettings(userId, req);

        assertThat(updated.getLeaderboardOptIn()).isTrue();
        assertThat(updated.getAnonymousMode()).isTrue();
    }
}

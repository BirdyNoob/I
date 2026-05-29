package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.entity.LearnerStats;
import com.icentric.Icentric.learning.entity.XpTransactionLog;
import com.icentric.Icentric.learning.repository.LearnerStatsRepository;
import com.icentric.Icentric.learning.repository.XpTransactionLogRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.LeaderboardResponse;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.LeaderboardRow;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.PrivacyUpdateRequest;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.GrantXpRequest;
import com.icentric.Icentric.learning.dto.LeaderboardDtos.GrantXpResponse;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class XpService {

    private final LearnerStatsRepository statsRepository;
    private final XpTransactionLogRepository transactionRepository;
    private final TenantSchemaService tenantSchemaService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;

    public record XpEarnedEvent(
            UUID userId,
            String eventType,
            String referenceEntityType,
            UUID referenceEntityId,
            int score,
            boolean isFirstAttempt,
            String tenantSlug
    ) {}

    /**
     * Publishes a lightweight asynchronous point event to the queue.
     */
    public void triggerXpEvent(UUID userId, String eventType, String referenceEntityType, UUID referenceEntityId, int score, boolean isFirstAttempt) {
        String tenant = TenantContext.getTenant();
        XpEarnedEvent event = new XpEarnedEvent(userId, eventType, referenceEntityType, referenceEntityId, score, isFirstAttempt, tenant);
        eventPublisher.publishEvent(event);
        log.debug("Published XpEarnedEvent for user {} - eventType: {}", userId, eventType);
    }

    /**
     * Listens to asynchronous XP events and executes point allocations inside tenant schemas.
     */
    @Async("playwrightTaskExecutor")
    @EventListener
    @Transactional
    public void handleXpEarnedEvent(XpEarnedEvent event) {
        log.info("Processing asynchronous XP event for user {} on tenant {}", event.userId(), event.tenantSlug());
        TenantContext.setTenant(event.tenantSlug());
        try {
            tenantSchemaService.applyCurrentTenantSearchPath();
            processXpAllocation(event.userId(), event.eventType(), event.referenceEntityType(), event.referenceEntityId(), event.score(), event.isFirstAttempt());
        } catch (Exception e) {
            log.error("Failed to process async XP event for user {}", event.userId(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Centralized execution pipeline for point allocation and streak calculations.
     */
    @Transactional
    public void processXpAllocation(UUID userId, String eventType, String referenceEntityType, UUID referenceEntityId, int score, boolean isFirstAttempt) {
        // 1. Assert Idempotency Guard
        if (referenceEntityId != null && transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, eventType, referenceEntityId)) {
            log.warn("Idempotency match: user {} already credited for event {} with ID {}. Skipping.", userId, eventType, referenceEntityId);
            return;
        }

        // 2. Fetch or Initialize Learner Stats
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant now = Instant.now();
        LearnerStats stats = statsRepository.findById(userId)
                .orElseGet(() -> {
                    LearnerStats s = new LearnerStats();
                    s.setUserId(userId);
                    s.setTotalXp(0);
                    s.setCurrentStreak(0);
                    s.setLongestStreak(0);
                    s.setLeaderboardOptIn(true);
                    s.setAnonymousMode(false);
                    s.setAnonymousAlias("Anonymous Learner #" + (Math.abs(userId.hashCode()) % 10000));
                    s.setCreatedAt(now);
                    s.setUpdatedAt(now);
                    return s;
                });

        // 3. Process streak allocations on daily lesson completion
        if ("LESSON_COMPLETED".equalsIgnoreCase(eventType)) {
            LocalDate lastActive = stats.getLastActiveDate();
            if (lastActive == null) {
                stats.setCurrentStreak(1);
                stats.setLongestStreak(1);
                stats.setLastActiveDate(today);
                // Award daily streak refill bonus
                awardStreakBonus(userId, today, stats);
            } else if (lastActive.equals(today.minusDays(1))) {
                stats.setCurrentStreak(stats.getCurrentStreak() + 1);
                stats.setLongestStreak(Math.max(stats.getLongestStreak(), stats.getCurrentStreak()));
                stats.setLastActiveDate(today);
                // Award daily streak refill bonus
                awardStreakBonus(userId, today, stats);
            } else if (!lastActive.equals(today)) {
                // Streak broken
                stats.setCurrentStreak(1);
                stats.setLastActiveDate(today);
                // Award daily streak refill bonus
                awardStreakBonus(userId, today, stats);
            }
        }

        // 4. Calculate Base Score and Streak Multiplier
        int points = 0;
        switch (eventType.toUpperCase()) {
            case "LESSON_COMPLETED":
                // Standard 20 XP. Apply 1.2x streak multiplier if streak >= 7 days
                int baseLessonXp = 20;
                if (stats.getCurrentStreak() >= 7) {
                    baseLessonXp = (int) Math.round(20 * 1.2);
                    log.info("User {} awarded 1.2x streak multiplier. Base: 20 -> 24 XP", userId);
                }
                points = baseLessonXp;
                break;
            case "COURSE_COMPLETED":
                points = 150;
                break;
            case "QUIZ_PASSED":
                points = isFirstAttempt ? 100 : 40;
                break;
            case "MANUAL_GRANT":
                points = score; // Passed as score parameter for manual overrides
                break;
        }

        if (points > 0) {
            writeTransactionAndSave(userId, points, eventType, referenceEntityType, referenceEntityId, stats, now);
        }

        // 5. Handle perfect quiz score bonus (score == 100)
        if ("QUIZ_PASSED".equalsIgnoreCase(eventType) && score == 100) {
            if (!transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "PERFECT_SCORE_BONUS", referenceEntityId)) {
                writeTransactionAndSave(userId, 30, "PERFECT_SCORE_BONUS", referenceEntityType, referenceEntityId, stats, now);
            }
        }
    }

    private void awardStreakBonus(UUID userId, LocalDate date, LearnerStats stats) {
        Instant now = Instant.now();
        UUID referenceId = UUID.nameUUIDFromBytes(date.toString().getBytes());
        if (!transactionRepository.existsByUserIdAndEventTypeAndReferenceEntityId(userId, "DAILY_STREAK", referenceId)) {
            writeTransactionAndSave(userId, 10, "DAILY_STREAK", "DATE", referenceId, stats, now);
            log.info("User {} awarded 10 XP daily streak bonus for date {}", userId, date);
        }
    }

    private void writeTransactionAndSave(UUID userId, int points, String eventType, String refType, UUID refId, LearnerStats stats, Instant now) {
        XpTransactionLog logEntry = new XpTransactionLog();
        logEntry.setId(UUID.randomUUID());
        logEntry.setUserId(userId);
        logEntry.setXpGranted(points);
        logEntry.setEventType(eventType);
        logEntry.setReferenceEntityType(refType);
        logEntry.setReferenceEntityId(refId);
        logEntry.setCreatedAt(now);
        transactionRepository.save(logEntry);

        stats.setTotalXp(stats.getTotalXp() + points);
        stats.setUpdatedAt(now);
        statsRepository.save(stats);
    }

    /**
     * Retrieves the leaderboard rankings for the tenant, respecting privacy settings.
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboardView(UUID userId, Pageable pageable) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        // 1. Fetch or Mock current user stats
        LearnerStats userStats = statsRepository.findById(userId).orElse(null);
        int userCurrentXp = userStats != null ? userStats.getTotalXp() : 0;
        int currentStreak = userStats != null ? userStats.getCurrentStreak() : 0;
        int longestStreak = userStats != null ? userStats.getLongestStreak() : 0;
        boolean anonymousMode = userStats != null ? userStats.getAnonymousMode() : false;
        boolean optIn = userStats != null ? userStats.getLeaderboardOptIn() : true;

        // Calculate global rank if opted-in
        int userCurrentRank = 0;
        if (optIn) {
            userCurrentRank = (int) statsRepository.countByLeaderboardOptInTrueAndTotalXpGreaterThan(userCurrentXp) + 1;
        }

        // 2. Fetch page of opted-in stats ordered by total XP
        Page<LearnerStats> page = statsRepository.findByLeaderboardOptInTrueOrderByTotalXpDescUserIdAsc(pageable);

        // 3. Resolve Display Names and Departments in batch to avoid N+1 queries
        List<UUID> userIds = page.getContent().stream().map(LearnerStats::getUserId).toList();
        Map<UUID, User> userMap = new HashMap<>();
        Map<UUID, TenantUser> membershipMap = new HashMap<>();

        if (!userIds.isEmpty()) {
            List<User> users = userRepository.findAllById(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

            String tenantSlug = TenantContext.getTenant();
            if (tenantSlug != null && !tenantSlug.isBlank()) {
                Optional<Tenant> tenantOpt = tenantRepository.findBySlug(tenantSlug);
                if (tenantOpt.isPresent()) {
                    List<TenantUser> memberships = tenantUserRepository.findByTenantIdAndUserIdIn(tenantOpt.get().getId(), userIds);
                    membershipMap = memberships.stream().collect(Collectors.toMap(TenantUser::getUserId, m -> m, (a, b) -> a));
                }
            }
        }

        // 4. Build Ranked rows
        List<LeaderboardRow> rankings = new ArrayList<>();
        List<LearnerStats> contentList = page.getContent();
        for (int i = 0; i < contentList.size(); i++) {
            LearnerStats s = contentList.get(i);
            int rank = page.getNumber() * page.getSize() + i + 1;
            String displayName;
            String departmentName = "General";

            if (s.getAnonymousMode()) {
                displayName = s.getAnonymousAlias() != null ? s.getAnonymousAlias() : "Anonymous Learner";
                departmentName = "UNKNOWN";
            } else {
                User u = userMap.get(s.getUserId());
                displayName = u != null ? u.getName() : "Unknown User";

                TenantUser tu = membershipMap.get(s.getUserId());
                if (tu != null && tu.getDepartment() != null) {
                    departmentName = tu.getDepartment().getDisplayName();
                }
            }

            boolean isCurrentUser = s.getUserId().equals(userId);

            rankings.add(LeaderboardRow.builder()
                    .rank(rank)
                    .displayName(displayName)
                    .department(departmentName)
                    .totalXp(s.getTotalXp())
                    .streak(s.getCurrentStreak())
                    .isCurrentUser(isCurrentUser)
                    .build());
        }

        return LeaderboardResponse.builder()
                .userCurrentRank(userCurrentRank)
                .userCurrentXp(userCurrentXp)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .anonymousMode(anonymousMode)
                .optIn(optIn)
                .rankings(rankings)
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    /**
     * Updates privacy opt-in and anonymity settings for a learner.
     */
    @Transactional
    public LearnerStats updatePrivacySettings(UUID userId, PrivacyUpdateRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        Instant now = Instant.now();
        LearnerStats stats = statsRepository.findById(userId)
                .orElseGet(() -> {
                    LearnerStats s = new LearnerStats();
                    s.setUserId(userId);
                    s.setTotalXp(0);
                    s.setCurrentStreak(0);
                    s.setLongestStreak(0);
                    s.setLeaderboardOptIn(true);
                    s.setAnonymousMode(false);
                    s.setAnonymousAlias("Anonymous Learner #" + (Math.abs(userId.hashCode()) % 10000));
                    s.setCreatedAt(now);
                    s.setUpdatedAt(now);
                    return s;
                });

        stats.setLeaderboardOptIn(request.isLeaderboardOptIn());
        stats.setAnonymousMode(request.isAnonymousMode());
        stats.setUpdatedAt(now);
        return statsRepository.save(stats);
    }

    /**
     * Admin method to manually grant manual/override XP.
     */
    @Transactional
    public GrantXpResponse grantManualXp(UUID userId, int xpAmount, String reason) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        Instant now = Instant.now();
        LearnerStats stats = statsRepository.findById(userId)
                .orElseGet(() -> {
                    LearnerStats s = new LearnerStats();
                    s.setUserId(userId);
                    s.setTotalXp(0);
                    s.setCurrentStreak(0);
                    s.setLongestStreak(0);
                    s.setLeaderboardOptIn(true);
                    s.setAnonymousMode(false);
                    s.setAnonymousAlias("Anonymous Learner #" + (Math.abs(userId.hashCode()) % 10000));
                    s.setCreatedAt(now);
                    s.setUpdatedAt(now);
                    return s;
                });

        UUID refId = UUID.randomUUID();
        XpTransactionLog logEntry = new XpTransactionLog();
        logEntry.setId(refId);
        logEntry.setUserId(userId);
        logEntry.setXpGranted(xpAmount);
        logEntry.setEventType("MANUAL_GRANT");
        logEntry.setReferenceEntityType("ADMIN_OVERRIDE");
        logEntry.setReferenceEntityId(refId);
        logEntry.setCreatedAt(now);
        transactionRepository.save(logEntry);

        stats.setTotalXp(stats.getTotalXp() + xpAmount);
        stats.setUpdatedAt(now);
        statsRepository.save(stats);

        return GrantXpResponse.builder()
                .userId(userId.toString())
                .newTotalXp(stats.getTotalXp())
                .transactionId(refId.toString())
                .build();
    }
}

package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.LearnerStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LearnerStatsRepository extends JpaRepository<LearnerStats, UUID> {

    Page<LearnerStats> findByLeaderboardOptInTrueOrderByTotalXpDescUserIdAsc(Pageable pageable);

    long countByLeaderboardOptInTrueAndTotalXpGreaterThan(int totalXp);
}

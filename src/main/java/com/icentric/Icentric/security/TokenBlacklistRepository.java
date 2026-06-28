package com.icentric.Icentric.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface TokenBlacklistRepository extends JpaRepository<BlacklistedToken, String> {

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM BlacklistedToken t WHERE t.expiresAt < :now")
    int deleteExpired(Instant now);
}

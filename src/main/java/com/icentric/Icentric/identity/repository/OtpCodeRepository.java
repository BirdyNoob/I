package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.identity.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);

    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now")
    int deleteExpired(Instant now);
}

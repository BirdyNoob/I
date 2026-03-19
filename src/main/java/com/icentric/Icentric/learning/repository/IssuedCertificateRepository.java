package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.IssuedCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssuedCertificateRepository
        extends JpaRepository<IssuedCertificate, UUID> {

    List<IssuedCertificate> findByUserId(UUID userId);
    boolean existsByUserIdAndTrackId(UUID userId, UUID trackId);
}

package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.dto.CertificateDownloadData;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssuedCertificateRepository
        extends JpaRepository<IssuedCertificate, UUID> {

    List<IssuedCertificate> findByUserId(UUID userId);
    boolean existsByUserIdAndTrackId(UUID userId, UUID trackId);
    Optional<IssuedCertificate> findByUserIdAndTrackId(UUID userId, UUID trackId);

    @Query("""
SELECT new com.icentric.Icentric.learning.dto.CertificateDownloadData(
    ic.id,
    ic.userId,
    u.email,
    t.title,
    ic.issuedAt
)
FROM IssuedCertificate ic
JOIN com.icentric.Icentric.identity.entity.User u ON u.id = ic.userId
JOIN com.icentric.Icentric.content.entity.Track t ON t.id = ic.trackId
WHERE ic.userId = :userId
AND ic.trackId = :trackId
""")
    Optional<CertificateDownloadData> findCertificateDownloadData(UUID userId, UUID trackId);
}

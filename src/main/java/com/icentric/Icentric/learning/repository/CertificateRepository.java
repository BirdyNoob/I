package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository
        extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByTrackId(UUID trackId);
}

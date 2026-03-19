package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.learning.dto.CertificateResponse;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final IssuedCertificateRepository issuedRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;

    public CertificateService(
            CertificateRepository certificateRepository,
            IssuedCertificateRepository issuedRepository,
            LessonProgressRepository progressRepository,
            LessonRepository lessonRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.issuedRepository = issuedRepository;
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
    }

    public void checkAndIssue(UUID userId, UUID trackId) {

        boolean alreadyIssued =
                issuedRepository.existsByUserIdAndTrackId(userId, trackId);

        if (alreadyIssued) return;

        long completed =
                progressRepository.countCompletedLessons(userId, trackId);

        long total =
                lessonRepository.countLessonsInTrack(trackId);

        if (completed == total && total > 0) {

            var certificate = certificateRepository
                    .findByTrackId(trackId)
                    .orElse(null);

            if (certificate == null) return;

            IssuedCertificate issued = new IssuedCertificate();
            issued.setId(UUID.randomUUID());
            issued.setUserId(userId);
            issued.setTrackId(trackId);
            issued.setCertificateId(certificate.getId());
            issued.setIssuedAt(Instant.now());

            issuedRepository.save(issued);
        }

    }
    public List<CertificateResponse> getCertificates(UUID userId) {

        return issuedRepository.findByUserId(userId)
                .stream()
                .map(ic -> {

                    var cert = certificateRepository
                            .findById(ic.getCertificateId())
                            .orElseThrow();

                    return new CertificateResponse(
                            cert.getTitle(),
                            ic.getTrackId(),
                            ic.getIssuedAt()
                    );

                }).toList();
    }
}

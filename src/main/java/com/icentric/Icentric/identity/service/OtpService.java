package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.entity.OtpCode;
import com.icentric.Icentric.identity.repository.OtpCodeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OtpService {

    private static final long OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final long COOLDOWN_SECONDS = 60;

    private final OtpCodeRepository repository;

    public OtpService(OtpCodeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String generateOtp(String email) {
        String key = email.toLowerCase().trim();

        // Check cooldown
        repository.findTopByEmailOrderByCreatedAtDesc(key).ifPresent(existing -> {
            if (existing.getCreatedAt().plusSeconds(COOLDOWN_SECONDS).isAfter(Instant.now())) {
                throw new IllegalStateException("Please wait before requesting another OTP.");
            }
        });

        // Delete old OTPs for this email
        repository.deleteByEmail(key);

        // Generate and store new OTP
        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        OtpCode code = new OtpCode();
        code.setEmail(key);
        code.setOtp(otp);
        code.setExpiresAt(Instant.now().plusSeconds(OTP_EXPIRY_SECONDS));
        repository.save(code);

        return otp;
    }

    @Transactional
    public boolean verify(String email, String otp) {
        String key = email.toLowerCase().trim();
        var codeOpt = repository.findTopByEmailOrderByCreatedAtDesc(key);
        if (codeOpt.isEmpty()) return false;

        OtpCode code = codeOpt.get();
        if (code.getExpiresAt().isBefore(Instant.now())) {
            repository.deleteByEmail(key);
            return false;
        }
        if (code.getOtp().equals(otp)) {
            repository.deleteByEmail(key); // one-time use
            return true;
        }
        return false;
    }

    @Scheduled(fixedRate = 300_000) // every 5 min
    @Transactional
    public void cleanupExpired() {
        repository.deleteExpired(Instant.now());
    }
}

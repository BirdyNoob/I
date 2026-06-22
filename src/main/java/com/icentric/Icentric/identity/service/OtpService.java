package com.icentric.Icentric.identity.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OtpService {

    private static final long OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final long COOLDOWN_SECONDS = 60;    // 1 min between requests

    private record OtpEntry(String otp, Instant expiresAt, Instant createdAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
        boolean isInCooldown() { return Instant.now().isBefore(createdAt.plusSeconds(COOLDOWN_SECONDS)); }
    }

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    public String generateOtp(String email) {
        String key = email.toLowerCase().trim();

        OtpEntry existing = store.get(key);
        if (existing != null && existing.isInCooldown()) {
            throw new IllegalStateException("Please wait before requesting another OTP.");
        }

        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        store.put(key, new OtpEntry(otp, Instant.now().plusSeconds(OTP_EXPIRY_SECONDS), Instant.now()));
        return otp;
    }

    public boolean verify(String email, String otp) {
        String key = email.toLowerCase().trim();
        OtpEntry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            store.remove(key);
            return false;
        }
        if (entry.otp().equals(otp)) {
            store.remove(key); // one-time use
            return true;
        }
        return false;
    }
}

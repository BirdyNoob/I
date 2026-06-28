package com.icentric.Icentric.identity.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login attempt tracker. Locks accounts after 5 failed attempts for 15 minutes.
 * For multi-instance deployments, move to DB/Redis (similar to OTP migration).
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_SECONDS = 900; // 15 minutes

    private record AttemptState(int count, Instant lockedUntil) {}

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void loginFailed(String email) {
        String key = email.toLowerCase().trim();
        AttemptState state = attempts.get(key);
        int count = (state != null) ? state.count() + 1 : 1;

        Instant lockedUntil = null;
        if (count >= MAX_ATTEMPTS) {
            lockedUntil = Instant.now().plusSeconds(LOCKOUT_SECONDS);
        }
        attempts.put(key, new AttemptState(count, lockedUntil));
    }

    public void loginSucceeded(String email) {
        attempts.remove(email.toLowerCase().trim());
    }

    public boolean isLocked(String email) {
        String key = email.toLowerCase().trim();
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil() == null) return false;
        if (Instant.now().isAfter(state.lockedUntil())) {
            attempts.remove(key); // Lockout expired
            return false;
        }
        return true;
    }

    public long getRemainingLockSeconds(String email) {
        String key = email.toLowerCase().trim();
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil() == null) return 0;
        long remaining = state.lockedUntil().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}

package com.icentric.Icentric.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DatabaseRateLimiterService {

    private final RateLimitLockRepository repository;

    /**
     * Checks if a specific action key is currently locked.
     */
    @Transactional(readOnly = true)
    public boolean isLocked(String key) {
        return repository.findById(key)
                .map(lock -> lock.getLockedUntil().isAfter(Instant.now()))
                .orElse(false);
    }

    /**
     * Attempts to acquire a lock for a specified key.
     * If the lock already exists and has not expired, returns false.
     * Otherwise, creates or updates the lock and returns true.
     */
    @Transactional
    public boolean acquireLock(String key, Duration duration) {
        Instant now = Instant.now();
        Optional<RateLimitLock> lockOpt = repository.findById(key);

        if (lockOpt.isPresent() && lockOpt.get().getLockedUntil().isAfter(now)) {
            return false; // Already locked (cannot acquire)
        }

        RateLimitLock lock = lockOpt.orElseGet(RateLimitLock::new);
        lock.setKeyIdentifier(key);
        lock.setLockedUntil(now.plus(duration));
        lock.setUpdatedAt(now);
        if (lock.getCreatedAt() == null) {
            lock.setCreatedAt(now);
        }

        repository.save(lock);
        return true;
    }

    /**
     * Gets the remaining locked duration in seconds. Returns 0 if not locked or expired.
     */
    @Transactional(readOnly = true)
    public long getRemainingSeconds(String key) {
        return repository.findById(key)
                .map(lock -> Math.max(0L, Duration.between(Instant.now(), lock.getLockedUntil()).toSeconds()))
                .orElse(0L);
    }

    /**
     * Explicitly releases a lock (e.g. if an operation fails or needs recovery).
     */
    @Transactional
    public void releaseLock(String key) {
        repository.deleteById(key);
    }
}

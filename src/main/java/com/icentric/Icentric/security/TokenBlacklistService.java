package com.icentric.Icentric.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class TokenBlacklistService {

    private final TokenBlacklistRepository repository;

    public TokenBlacklistService(TokenBlacklistRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void blacklist(String token, Instant expiresAt) {
        BlacklistedToken entry = new BlacklistedToken();
        entry.setTokenHash(hash(token));
        entry.setExpiresAt(expiresAt);
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        return repository.existsByTokenHash(hash(token));
    }

    @Scheduled(fixedRate = 600_000) // every 10 min
    @Transactional
    public void cleanup() {
        repository.deleteExpired(Instant.now());
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.substring(token.length() - 32).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return token.substring(token.length() - 32);
        }
    }
}

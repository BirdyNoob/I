package com.icentric.Icentric.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

        private final SecretKey key;
        private final long tokenTtlMs;
        private final long impersonationTtlMs;

        public JwtService(
                        @Value("${icentric.jwt.secret}") String secret,
                        @Value("${icentric.jwt.token-ttl-ms:1800000}") long tokenTtlMs,
                        @Value("${icentric.jwt.impersonation-ttl-ms:7200000}") long impersonationTtlMs) {
                this.key = Keys.hmacShaKeyFor(secret.getBytes());
                this.tokenTtlMs = tokenTtlMs;
                this.impersonationTtlMs = impersonationTtlMs;
        }

        public Claims parse(String token) {
                return Jwts.parserBuilder()
                                .setSigningKey(key)
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
        }

        public String generateToken(
                        String email,
                        UUID userId,
                        String role,
                        String tenant) {
                return Jwts.builder()
                                .setSubject(email)
                                .claim("userId", userId)
                                .claim("role", role)
                                .claim("tenant", tenant)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + tokenTtlMs))
                                .signWith(key)
                                .compact();
        }

        public String generateImpersonationToken(
                        String email,
                        UUID userId,
                        String role,
                        String tenant,
                        UUID impersonatedBy,
                        UUID sessionId) {
                return Jwts.builder()
                                .setSubject(email)
                                .claim("userId", userId)
                                .claim("role", role)
                                .claim("tenant", tenant)
                                .claim("impersonatedBy", impersonatedBy)
                                .claim("impersonationSessionId", sessionId)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + impersonationTtlMs))
                                .signWith(key)
                                .compact();
        }
}
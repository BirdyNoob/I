package com.icentric.Icentric.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

public class JwtService {

    private final SecretKey key = Keys.hmacShaKeyFor(
            "mysupersecretkeymysupersecretkey".getBytes()
    );

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
            String tenant
    ) {

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("tenant", tenant)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1800000))
                .signWith(key)
                .compact();
    }
    public String generateImpersonationToken(
            String email,
            UUID userId,
            String role,
            String tenant,
            UUID impersonatedBy,
            UUID sessionId
    ) {

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("tenant", tenant)
                .claim("impersonatedBy", impersonatedBy)
                .claim("impersonationSessionId", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 7200000))
                .signWith(key)
                .compact();
    }
}
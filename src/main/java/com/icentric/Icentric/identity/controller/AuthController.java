package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /**
     * Tenant user login. Tenant slug is now in the request body.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    /**
     * Exchange a valid refresh token for a new access + refresh token pair.
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }
        return service.refresh(refreshToken);
    }

    /**
     * Revoke the refresh token (logout).
     */
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken != null) {
            service.logout(refreshToken);
        }
        return Map.of("status", "logged_out");
    }
}

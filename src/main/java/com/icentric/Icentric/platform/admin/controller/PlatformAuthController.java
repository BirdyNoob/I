package com.icentric.Icentric.platform.admin.controller;

import com.icentric.Icentric.platform.admin.dto.PlatformLoginRequest;
import com.icentric.Icentric.platform.admin.dto.PlatformLoginResponse;
import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.platform.admin.service.PlatformAuthService;
import com.icentric.Icentric.security.TokenBlacklistService;
import com.icentric.Icentric.security.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/auth")
@Validated
@Tag(name = "Platform Authentication", description = "APIs for platform admin login and MFA enrollment")
public class PlatformAuthController {

    private final PlatformAuthService authService;
    private final PlatformAdminRepository repository;
    private final MfaService mfaService;
    private final TokenBlacklistService tokenBlacklistService;
    private final com.icentric.Icentric.security.JwtService jwtService;

    public PlatformAuthController(PlatformAuthService authService, PlatformAdminRepository repository,
            MfaService mfaService, TokenBlacklistService tokenBlacklistService,
            com.icentric.Icentric.security.JwtService jwtService) {
        this.authService = authService;
        this.repository = repository;
        this.mfaService = mfaService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Platform admin login", description = "Authenticates a platform admin and returns access + refresh tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully logged in"),
            @ApiResponse(responseCode = "400", description = "Invalid login credentials or request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - incorrect password or missing MFA")
    })
    @PostMapping("/login")
    public PlatformLoginResponse login(@Valid @RequestBody PlatformLoginRequest request) { // Fix #5: @Valid
        return authService.login(request);
    }

    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token for a new access + refresh token pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public PlatformLoginResponse refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }
        return authService.refresh(refreshToken);
    }

    @Operation(summary = "Logout", description = "Revokes the refresh token to prevent further use.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Successfully logged out")
    })
    @PostMapping("/logout")
    public org.springframework.http.ResponseEntity<Void> logout(
            @RequestBody java.util.Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Blacklist access token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                var claims = jwtService.parse(token);
                tokenBlacklistService.blacklist(token, claims.getExpiration().toInstant());
            } catch (Exception ignored) {}
        }
        String refreshToken = body.get("refreshToken");
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @Operation(summary = "Enroll in MFA", description = "Generates an MFA secret and returns a QR code image for a platform admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully generated QR code image"),
            @ApiResponse(responseCode = "400", description = "Invalid email parameter or platform admin not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/mfa/enroll")
    public byte[] enrollMfa(
            @Parameter(description = "Email of the platform admin") @RequestParam @NotBlank @Email String email
    ) throws Exception {

        PlatformAdmin admin = repository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No admin found with email: " + email));

        String secret = mfaService.generateSecret();
        admin.setMfaSecret(secret);
        admin.setMfaEnabled(true);
        repository.save(admin);
        return mfaService.generateQrCode(email, secret);
    }
}

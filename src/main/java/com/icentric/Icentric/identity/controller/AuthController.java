package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.dto.RefreshTokenRequest;
import com.icentric.Icentric.identity.dto.SelectTenantRequest;
import com.icentric.Icentric.identity.service.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(name = "Authentication", description = "APIs for user authentication, login, and token management")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /**
     * Global login. Returns tokens directly when the user belongs to a single tenant,
     * or a list of workspace choices when the user belongs to multiple tenants.
     */
    @Operation(summary = "User Login",
               description = "Authenticates a user globally with email and password. "
                       + "If the user belongs to a single tenant, returns access and refresh tokens. "
                       + "If the user belongs to multiple tenants, returns a list of workspaces to choose from.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid request format"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    /**
     * Called after login when the user belongs to multiple tenants.
     * The email is passed as a query parameter (kept from the previous login response
     * in the frontend's memory) so we can identify the user without a JWT.
     */
    @Operation(summary = "Select Tenant",
               description = "After a multi-tenant login, the user selects a workspace. "
                       + "Returns access and refresh tokens scoped to the chosen tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens issued for selected tenant"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or not a member of the selected tenant")
    })
    @PostMapping("/select-tenant")
    public LoginResponse selectTenant(
            @RequestParam String email,
            @Valid @RequestBody SelectTenantRequest request
    ) {
        return service.selectTenant(email, request);
    }

    /**
     * Exchange a valid refresh token for a new access + refresh token pair.
     */
    @Operation(summary = "Refresh Token",
               description = "Exchanges a valid refresh token for a new access and refresh token pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully refreshed token"),
            @ApiResponse(responseCode = "400", description = "Invalid request format"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    /**
     * Revoke the refresh token (logout).
     */
    @Operation(summary = "Logout User",
               description = "Revokes the provided refresh token, effectively logging the user out.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully logged out"),
            @ApiResponse(responseCode = "400", description = "Invalid request format")
    })
    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody RefreshTokenRequest request,
                                      jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Blacklist the access token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            service.blacklistAccessToken(authHeader.substring(7));
        }
        service.logout(request.refreshToken());
        return Map.of("status", "logged_out");
    }

    @Operation(summary = "Forgot Password",
               description = "Sends a 6-digit OTP to the user's email. OTP expires in 5 minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent if email exists"),
            @ApiResponse(responseCode = "400", description = "Invalid email format")
    })
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        service.forgotPassword(email.trim().toLowerCase());
        return Map.of("message", "If the email exists, an OTP has been sent.");
    }

    @Operation(summary = "Reset Password",
               description = "Verifies OTP and sets a new password. OTP is single-use.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successful"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired OTP")
    })
    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        String newPassword = body.get("newPassword");
        if (email == null || otp == null || newPassword == null) {
            throw new IllegalArgumentException("email, otp, and newPassword are required");
        }
        if (newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        service.resetPassword(email.trim().toLowerCase(), otp.trim(), newPassword);
        return Map.of("message", "Password has been reset successfully.");
    }
}

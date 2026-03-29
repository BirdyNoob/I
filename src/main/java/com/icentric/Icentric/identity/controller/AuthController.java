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
    public Map<String, String> logout(@Valid @RequestBody RefreshTokenRequest request) {
        service.logout(request.refreshToken());
        return Map.of("status", "logged_out");
    }
}

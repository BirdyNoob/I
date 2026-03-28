package com.icentric.Icentric.platform.admin.controller;

import com.icentric.Icentric.platform.admin.dto.PlatformLoginRequest;
import com.icentric.Icentric.platform.admin.dto.PlatformLoginResponse;
import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.platform.admin.service.PlatformAuthService;
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

    public PlatformAuthController(PlatformAuthService authService, PlatformAdminRepository repository,
            MfaService mfaService) {
        this.authService = authService;
        this.repository = repository;
        this.mfaService = mfaService;
    }

    @Operation(summary = "Platform admin login", description = "Authenticates a platform admin and returns a JWT token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully logged in"),
            @ApiResponse(responseCode = "400", description = "Invalid login credentials or request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - incorrect password or missing MFA")
    })
    @PostMapping("/login")
    public PlatformLoginResponse login(@Valid @RequestBody PlatformLoginRequest request) { // Fix #5: @Valid
        return authService.login(request);
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

package com.icentric.Icentric.platform.admin.controller;

import com.icentric.Icentric.platform.admin.dto.PlatformLoginRequest;
import com.icentric.Icentric.platform.admin.dto.PlatformLoginResponse;
import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.platform.admin.service.PlatformAuthService;
import com.icentric.Icentric.security.MfaService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/auth")
@Validated
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

    @PostMapping("/login")
    public PlatformLoginResponse login(@Valid @RequestBody PlatformLoginRequest request) { // Fix #5: @Valid
        return authService.login(request);
    }

    @PostMapping("/mfa/enroll")
    public byte[] enrollMfa(@RequestParam @NotBlank @Email String email) throws Exception {

        PlatformAdmin admin = repository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No admin found with email: " + email));

        String secret = mfaService.generateSecret();
        admin.setMfaSecret(secret);
        admin.setMfaEnabled(true);
        repository.save(admin);
        return mfaService.generateQrCode(email, secret);
    }
}

package com.icentric.Icentric.platform.admin.service;

import com.icentric.Icentric.platform.admin.dto.PlatformLoginRequest;
import com.icentric.Icentric.platform.admin.dto.PlatformLoginResponse;
import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.security.MfaService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuthService {

    private final PlatformAdminRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MfaService mfaService;

    public PlatformAuthService(
            PlatformAdminRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MfaService mfaService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mfaService = mfaService;
    }

    public PlatformLoginResponse login(PlatformLoginRequest request) {

        PlatformAdmin admin = repository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (admin.getMfaEnabled()) {

            boolean valid = mfaService.verifyCode(
                    admin.getMfaSecret(),
                    request.mfaCode());

            if (!valid) {
                throw new BadCredentialsException("Invalid MFA code");
            }
        }

        String token = jwtService.generateToken(
                admin.getEmail(),
                admin.getId(),
                "ROLE_PLATFORM_ADMIN",
                "system");

        return new PlatformLoginResponse(token);
    }
}

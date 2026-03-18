package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.service.AuthService;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestHeader("X-Tenant") String tenant,
            @RequestBody LoginRequest request
    ) {
        return service.login(tenant, request);
    }
}

package com.icentric.Icentric.platform.controller;

import com.icentric.Icentric.platform.dto.TenantResponse;
import com.icentric.Icentric.platform.service.PlatformUserService;
import com.icentric.Icentric.platform.tenant.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformUserController {

    private final PlatformUserService service;
    private final TenantService tenantService;

    public PlatformUserController(PlatformUserService service, TenantService tenantService) {
        this.service = service;
        this.tenantService = tenantService;
    }

    @GetMapping("/{tenantId}/users")
    public List<Map<String, Object>> getUsers(
            @PathVariable UUID tenantId
    ) {
        return service.getTenantUsers(tenantId);
    }
    @GetMapping
    public List<TenantResponse> getTenants() {
        return tenantService.getAllTenants();
    }
}

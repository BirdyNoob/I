package com.icentric.Icentric.platform.admin.dto;

public record PlatformLoginRequest(
        String email,
        String password,
        String mfaCode
) {}
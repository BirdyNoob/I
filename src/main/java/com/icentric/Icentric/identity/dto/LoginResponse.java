package com.icentric.Icentric.identity.dto;

public record LoginResponse(

        String accessToken,
        String refreshToken

) {}

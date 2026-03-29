package com.icentric.Icentric.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CertificateUrlService {

    private final String publicBaseUrl;

    public CertificateUrlService(
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    public String downloadUrl(UUID certificateId, UUID verificationToken) {
        return publicBaseUrl + "/api/v1/public/certificates/" + certificateId + "/download?token=" + verificationToken;
    }

    public String verificationUrl(UUID certificateId, UUID verificationToken) {
        return publicBaseUrl + "/api/v1/public/certificates/" + certificateId + "/verify?token=" + verificationToken;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

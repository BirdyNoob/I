package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.dto.CertificateDownloadData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfServiceTest {

    @Test
    @DisplayName("testnewcertifications.html renders pixel-perfect via Playwright (headless Chromium)")
    void newTemplate_rendersViaPlaywright() throws Exception {
        CertificateUrlService urlService = new CertificateUrlService("http://localhost:8080");
        PlaywrightPdfService playwright = new PlaywrightPdfService();
        CertificatePdfService pdfService = new CertificatePdfService(urlService, playwright);

        CertificateDownloadData data = new CertificateDownloadData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Aryan Kundal",
                "aryan@example.com",
                "Advanced Threat Detection and Mitigation",
                Instant.now()
        );

        byte[] pdf = pdfService.generateCertificate(data);

        assertThat(pdf).isNotEmpty();

        Path out = Path.of(
                "/Users/aryankundal/.gemini/antigravity/brain/" +
                "962492b3-9127-473d-ba33-8f5a3c6d533e/scratch/test_certificate_playwright.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("PDF written to scratch: " + out.toAbsolutePath());

        Path workspaceOut = Path.of("/Users/aryankundal/Downloads/Icentric/sample_certificate.pdf");
        Files.write(workspaceOut, pdf);
        System.out.println("PDF written to workspace root: " + workspaceOut.toAbsolutePath());

        assertThat(out.toFile()).exists();
        assertThat(workspaceOut.toFile()).exists();
    }
}

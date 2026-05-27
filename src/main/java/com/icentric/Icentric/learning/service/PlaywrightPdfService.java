package com.icentric.Icentric.learning.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders HTML templates to PDF bytes using Playwright (headless Chromium).
 *
 * Unlike {@code openhtmltopdf} which is limited to CSS 2.1, Playwright uses the
 * full Chrome rendering engine and supports all modern CSS including flexbox,
 * CSS Grid, gradients, box-shadow, backdrop-filter, custom properties, etc.
 *
 * Usage: inject this service and call {@link #render(String)} with the fully
 * substituted HTML string to receive a PDF byte array.
 */
@Service
public class PlaywrightPdfService {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightPdfService.class);

    /**
     * Renders the given HTML string to a PDF byte array.
     * The PDF is formatted as A4 landscape to match the certificate design.
     *
     * @param html fully substituted HTML (all {{placeholders}} already resolved)
     * @return raw PDF bytes
     */
    public byte[] render(String html) {
        return render(html, true);
    }

    /**
     * Renders the given HTML string to a PDF byte array with specified orientation.
     *
     * @param html fully substituted HTML
     * @param landscape true for landscape A4, false for portrait A4
     * @return raw PDF bytes
     */
    public byte[] render(String html, boolean landscape) {
        // Write HTML to a temp file so Playwright can load it via file:// URL.
        // This is required for relative resources and correct base-URI resolution.
        Path tmpHtml = null;
        try {
            tmpHtml = Files.createTempFile("icentric-cert-", ".html");
            Files.writeString(tmpHtml, html);

            try (Playwright playwright = Playwright.create()) {
                BrowserType chromium = playwright.chromium();
                try (Browser browser = chromium.launch()) {
                    BrowserContext ctx = browser.newContext();
                    Page page = ctx.newPage();

                    // Set absolute timeout of 15 seconds to prevent browser hangups
                    page.setDefaultTimeout(15000);

                    // Load the HTML file
                    page.navigate("file://" + tmpHtml.toAbsolutePath());

                    // Wait for fonts and layout to fully settle
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

                    // Print to PDF — landscape A4, no browser margin (template handles its own padding)
                    com.microsoft.playwright.options.Margin noMargin =
                            new com.microsoft.playwright.options.Margin()
                                     .setTop("0").setBottom("0").setLeft("0").setRight("0");

                    byte[] pdf = page.pdf(new Page.PdfOptions()
                            .setFormat("A4")
                            .setLandscape(landscape)
                            .setPrintBackground(true)
                            .setMargin(noMargin)
                    );

                    log.info("Playwright rendered PDF (landscape={}): {} bytes", landscape, pdf.length);
                    return pdf;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to render PDF via Playwright", e);
        } finally {
            if (tmpHtml != null) {
                try { Files.deleteIfExists(tmpHtml); } catch (Exception ignored) {}
            }
        }
    }
}

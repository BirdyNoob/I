package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.dto.CertificateDownloadData;
import com.icentric.Icentric.learning.exception.CertificateGenerationException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRRuntimeException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CertificatePdfService {
    private static final DateTimeFormatter ISSUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu").withZone(ZoneOffset.UTC);
    private static final String TEMPLATE_PATH = "templates/certificates/cybersecurity_certificate.html";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9]+)}}");

    private final CertificateUrlService certificateUrlService;

    public CertificatePdfService(CertificateUrlService certificateUrlService) {
        this.certificateUrlService = certificateUrlService;
    }

    public byte[] generateCertificate(CertificateDownloadData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String html = renderTemplate(data);

        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new CertificateGenerationException(
                    "Failed to render certificate PDF output for userEmail: " + data.userEmail()
                            + ", trackTitle: " + data.trackTitle(),
                    e
            );
        } catch (XRRuntimeException e) {
            throw new CertificateGenerationException(
                    "Failed to layout certificate template for userEmail: " + data.userEmail()
                            + ", trackTitle: " + data.trackTitle(),
                    e
            );
        }
    }

    private String renderTemplate(CertificateDownloadData data) {
        String template = loadTemplate();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("platformNameStart", "AI");
        placeholders.put("platformNameHighlight", "SAFE");
        placeholders.put("platformNameEnd", " PROTOCOL");
        placeholders.put("recipientName", escape(resolveRecipientName(data)));
        placeholders.put("trackTitle", escape(data.trackTitle()));
        placeholders.put("certificateId", escape(shortId(data.certificateId())));
        placeholders.put("learnerId", escape(shortId(data.learnerId())));
        placeholders.put("issuedDate", escape(formatIssuedDate(data.issuedAt())));
        placeholders.put("tenantName", "ICENTRIC LEARNING");

        String verificationUrl = certificateUrlService.verificationUrl(data.certificateId(), data.verificationToken());
        placeholders.put("qrCode", generateQRCodeBase64(verificationUrl));

        return applyPlaceholders(template, placeholders);
    }

    private String loadTemplate() {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CertificateGenerationException(
                    "Failed to load certificate template from classpath: " + TEMPLATE_PATH,
                    e
            );
        }
    }

    private String formatIssuedDate(Instant issuedAt) {
        return ISSUE_DATE_FORMAT.format(issuedAt);
    }

    private String shortId(java.util.UUID id) {
        return id == null ? "N/A" : id.toString().substring(0, 8).toUpperCase();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String resolveRecipientName(CertificateDownloadData data) {
        if (data.userName() != null && !data.userName().isBlank()) {
            return data.userName();
        }
        return data.userEmail();
    }

    private String generateQRCodeBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            throw new CertificateGenerationException("Failed to generate QR Code", e);
        }
    }

    private String applyPlaceholders(String template, Map<String, String> placeholders) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder(template.length() + 256);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = placeholders.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(rendered);
        return rendered.toString();
    }
}

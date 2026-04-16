package com.icentric.Icentric.common.mail;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

    /**
     * Sends an HTML email purely from a string content.
     */
    CompletableFuture<Void> sendHtmlEmail(String to, String subject, String htmlBody);

    /**
     * Sends an HTML email using a Thymeleaf template.
     *
     * @param to           Recipient email address
     * @param subject      Subject of the email
     * @param templateName The name of the template file in src/main/resources/templates/email/
     * @param variables    Key-value pairs to inject into the template
     */
    CompletableFuture<Void> sendTemplateEmail(String to, String subject, String templateName, Map<String, Object> variables);
}

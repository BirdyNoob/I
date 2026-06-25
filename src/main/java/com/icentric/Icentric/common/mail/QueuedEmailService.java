package com.icentric.Icentric.common.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Queued email implementation — writes to email_queue table.
 * Actual sending is handled by EmailQueueConsumer.
 * For emails with attachments (PDFs), sends directly via SMTP (can't queue binary).
 */
@Service
@Primary
public class QueuedEmailService implements EmailService {

    private final EmailQueueRepository queueRepository;
    private final SmtpEmailServiceImpl smtpService;
    private final ObjectMapper objectMapper;

    public QueuedEmailService(EmailQueueRepository queueRepository,
                              SmtpEmailServiceImpl smtpService,
                              ObjectMapper objectMapper) {
        this.queueRepository = queueRepository;
        this.smtpService = smtpService;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Void> sendHtmlEmail(String to, String subject, String htmlBody) {
        // HTML emails without template go through queue with special template name
        enqueue(to, subject, "__raw_html__", Map.of("body", htmlBody));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendTemplateEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        enqueue(to, subject, templateName, variables);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendEmailWithAttachment(String to, String subject, String htmlBody, byte[] attachmentBytes, String attachmentFilename) {
        // Attachments can't be queued (binary) — send directly
        return smtpService.sendEmailWithAttachment(to, subject, htmlBody, attachmentBytes, attachmentFilename);
    }

    private void enqueue(String to, String subject, String templateName, Map<String, Object> variables) {
        EmailQueueEntry entry = new EmailQueueEntry();
        entry.setRecipient(to);
        entry.setSubject(subject);
        entry.setTemplateName(templateName);
        try {
            entry.setVariables(objectMapper.writeValueAsString(variables));
        } catch (Exception e) {
            entry.setVariables("{}");
        }
        queueRepository.save(entry);
    }
}

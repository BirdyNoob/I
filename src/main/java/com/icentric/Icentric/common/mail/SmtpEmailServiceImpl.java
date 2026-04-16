package com.icentric.Icentric.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.properties.mail.from}")
    private String defaultFromAddress;

    @Override
    @Async("mailTaskExecutor")
    @Retryable(
            value = {MailException.class, MessagingException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public CompletableFuture<Void> sendHtmlEmail(String to, String subject, String htmlBody) {
        log.info("Preparing to send email to {}, subject: '{}'", to, subject);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(defaultFromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true sets validation as HTML

            javaMailSender.send(message);
            log.info("Email successfully sent to {}", to);
            return CompletableFuture.completedFuture(null);

        } catch (MessagingException e) {
            log.error("Failed to construct email for {}: {}", to, e.getMessage());
            // Rethrowing as RuntimeException so Spring Retry can catch it if needed,
            // or allowing completable future to complete exceptionally.
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }

    @Override
    @Async("mailTaskExecutor")
    @Retryable(
            value = {MailException.class, MessagingException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public CompletableFuture<Void> sendTemplateEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        log.info("Preparing template '{}' email to {}", templateName, to);

        Context context = new Context();
        context.setVariables(variables);
        
        // Ensure path resolves to templates/email/[templateName].html
        String templatePath = "email/" + templateName;
        String htmlBody = templateEngine.process(templatePath, context);

        return sendHtmlEmail(to, subject, htmlBody);
    }
}

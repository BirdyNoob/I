package com.icentric.Icentric.common.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class EmailQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueConsumer.class);
    private static final int MAX_RETRIES = 5;

    private final EmailQueueRepository queueRepository;
    private final SmtpEmailServiceImpl smtpService;
    private final ObjectMapper objectMapper;

    public EmailQueueConsumer(EmailQueueRepository queueRepository,
                              SmtpEmailServiceImpl smtpService,
                              ObjectMapper objectMapper) {
        this.queueRepository = queueRepository;
        this.smtpService = smtpService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 10_000) // every 10 seconds
    @SchedulerLock(name = "emailQueueConsumer", lockAtLeastFor = "5s", lockAtMostFor = "2m")
    @Transactional
    public void processQueue() {
        List<EmailQueueEntry> batch = queueRepository.findPendingBatch();
        for (EmailQueueEntry entry : batch) {
            try {
                send(entry);
                entry.setStatus("SENT");
                entry.setProcessedAt(Instant.now());
            } catch (Exception e) {
                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setErrorMessage(e.getMessage());
                if (entry.getRetryCount() >= MAX_RETRIES) {
                    entry.setStatus("FAILED");
                }
                log.error("Email send failed for {}: {}", entry.getRecipient(), e.getMessage());
            }
            queueRepository.save(entry);
        }
    }

    private void send(EmailQueueEntry entry) {
        Map<String, Object> vars = parseVariables(entry.getVariables());

        if ("__raw_html__".equals(entry.getTemplateName())) {
            String body = (String) vars.getOrDefault("body", "");
            smtpService.sendHtmlEmail(entry.getRecipient(), entry.getSubject(), body).join();
        } else {
            smtpService.sendTemplateEmail(entry.getRecipient(), entry.getSubject(),
                    entry.getTemplateName(), vars).join();
        }
    }

    private Map<String, Object> parseVariables(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}

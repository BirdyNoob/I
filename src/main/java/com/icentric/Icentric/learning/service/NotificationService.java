package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.AdminNotificationResponse;
import com.icentric.Icentric.learning.dto.NotificationResponse;
import com.icentric.Icentric.learning.constants.NotificationType;
import com.icentric.Icentric.learning.entity.NotificationEvent;
import com.icentric.Icentric.learning.repository.NotificationRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final TenantSchemaService tenantSchemaService;
    private final EmailService emailService;

    public NotificationService(
            NotificationRepository repository,
            UserRepository userRepository,
            TenantSchemaService tenantSchemaService,
            EmailService emailService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.emailService = emailService;
    }

    @Transactional
    public void createNotification(UUID userId, NotificationType type, String message) {
        createNotification(userId, type, message, null);
    }

    @Transactional
    public void createNotification(UUID userId, NotificationType type, String message, String eventKey) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        if (eventKey != null && !eventKey.isBlank() && repository.existsByEventKey(eventKey)) {
            return;
        }

        boolean alreadyQueued =
                repository.existsByUserIdAndTypeAndSentFalse(userId, type);

        if (eventKey == null && alreadyQueued) {
            return;
        }

        NotificationEvent event = new NotificationEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setType(type);
        event.setEventKey(eventKey);
        event.setMessage(message);
        event.setSent(false);
        event.setCreatedAt(Instant.now());

        repository.save(event);
    }

    @Transactional
    public void processNotifications() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        var events = repository.findBySentFalse();

        for (var event : events) {
            try {
                sendNotificationEmail(event);
                event.setSent(true);
                repository.save(event);
            } catch (RuntimeException ex) {
                log.error(
                        "Failed to send notification {} to user {}",
                        event.getId(),
                        event.getUserId(),
                        ex
                );
            }
        }
    }

    private void sendNotificationEmail(NotificationEvent event) {
        var user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Notification user not found: " + event.getUserId()));

        String recipient = user.getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Notification user has no email: " + event.getUserId());
        }

        String subject = notificationSubject(event.getType());
        String htmlBody = notificationEmailBody(
                user.getName() != null && !user.getName().isBlank() ? user.getName() : recipient,
                event
        );

        try {
            emailService.sendHtmlEmail(recipient, subject, htmlBody).join();
        } catch (CompletionException ex) {
            throw new IllegalStateException("SMTP send failed for notification " + event.getId(), ex.getCause());
        }
    }

    private String notificationSubject(NotificationType type) {
        Map<NotificationType, String> subjects = Map.of(
                NotificationType.REMINDER, "Training reminder",
                NotificationType.OVERDUE, "Training overdue",
                NotificationType.ESCALATION, "Training escalation alert",
                NotificationType.FAILED, "Training notification"
        );
        return subjects.getOrDefault(type, "Training notification");
    }

    private String notificationEmailBody(String displayName, NotificationEvent event) {
        String safeName = escapeHtml(displayName);
        String safeType = escapeHtml(event.getType().name());
        String safeMessage = escapeHtml(event.getMessage() == null ? "" : event.getMessage()).replace("\n", "<br>");

        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#f6f7fb;font-family:Arial,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f7fb;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                          <tr>
                            <td style="padding:24px 28px;background:#0f172a;color:#ffffff;">
                              <h1 style="margin:0;font-size:20px;line-height:28px;">AISafe Notification</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <p style="margin:0 0 16px;font-size:16px;line-height:24px;">Hi %s,</p>
                              <p style="margin:0 0 18px;font-size:15px;line-height:23px;">%s</p>
                              <p style="margin:0;color:#6b7280;font-size:13px;line-height:20px;">Notification type: %s</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, safeMessage, safeType);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> getAdminNotifications(UUID userId, Pageable pageable) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        return repository.findByUserId(userId, pageable)
                .map(event -> {

                    var user = userRepository.findById(event.getUserId())
                            .orElseThrow();

                    return new AdminNotificationResponse(
                            event.getId(),
                            event.getUserId(),
                            user.getEmail(),
                            event.getType(),
                            event.getMessage(),
                            event.getSent(),
                            Boolean.TRUE.equals(event.getIsRead()),
                            event.getCreatedAt()
                    );
                });
    }
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(
            UUID userId,
            NotificationType type,
            Pageable pageable
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Page<NotificationEvent> page;

        if (type != null) {
            page = repository.findByUserIdAndType(userId, type, pageable);
        } else {
            page = repository.findByUserId(userId, pageable);
        }

        return page.map(n -> new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getIsRead(),
                n.getCreatedAt()
        ));
    }
    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        var n = repository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));

        if (!n.getUserId().equals(userId)) {
            throw new AccessDeniedException("Unauthorized access");
        }

        if (!Boolean.TRUE.equals(n.getIsRead())) {
            n.setIsRead(true);
            repository.save(n);
        }
    }
    @Transactional
    public void markAllAsRead(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        repository.markAllAsRead(userId);
    }
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        return repository.countByUserIdAndIsReadFalse(userId);
    }

}

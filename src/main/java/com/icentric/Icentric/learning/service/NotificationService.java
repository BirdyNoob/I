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
        String displayName = user.getName() != null && !user.getName().isBlank() ? user.getName() : recipient;

        // Note: the notification message may contain the course name and due date.
        // It comes from the job that creates the notification.
        String safeMessage = event.getMessage() != null ? event.getMessage().replace("\n", "<br>") : "";
        
        String pillText = "🔔  NOTIFICATION";
        String title = "You have a new notification";
        switch (event.getType()) {
            case REMINDER -> {
                pillText = "⏰  REMINDER";
                title = "Training Reminder";
            }
            case OVERDUE -> {
                pillText = "🔴  OVERDUE";
                title = "Training is Overdue";
            }
            case ESCALATION -> {
                pillText = "⚠️  ESCALATION";
                title = "Training Escalation Alert";
            }
            case FAILED -> {
                pillText = "❌  FAILED";
                title = "Assessment Failed";
            }
        }

        Map<String, Object> variables = Map.of(
                "tenantName", "AISafe", // Default if no tenant info is on the notification directly
                "notificationPill", pillText,
                "displayName", displayName,
                "title", title,
                "message", safeMessage
        );

        try {
            emailService.sendTemplateEmail(recipient, subject, "AISafe_Email_Notification", variables).join();
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

    // `notificationEmailBody` and `escapeHtml` are removed as they are no longer needed.
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

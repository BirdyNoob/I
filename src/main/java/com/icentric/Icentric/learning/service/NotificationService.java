package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.AdminNotificationResponse;
import com.icentric.Icentric.learning.dto.NotificationResponse;
import com.icentric.Icentric.learning.entity.NotificationEvent;
import com.icentric.Icentric.learning.repository.NotificationRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final TenantSchemaService tenantSchemaService;

    public NotificationService(
            NotificationRepository repository,
            UserRepository userRepository,
            TenantSchemaService tenantSchemaService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Transactional
    public void createNotification(UUID userId, String type, String message) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        boolean alreadyQueued =
                repository.existsByUserIdAndTypeAndSentFalse(userId, type);

        if (alreadyQueued) {
            return;
        }

        NotificationEvent event = new NotificationEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setType(type);
        event.setMessage(message);
        event.setSent(false);
        event.setCreatedAt(Instant.now());

        repository.save(event);
    }

    @Transactional
    public void processNotifications() {
        tenantSchemaService.applyCurrentTenantSearchPath();

        var events = repository.findBySentFalse();

        for (var e : events) {

            // 🔥 Replace later with real email service
            System.out.println("Sending notification to user " + e.getUserId());
            System.out.println("Message: " + e.getMessage());

            e.setSent(true);
            repository.save(e);
        }
    }
    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> getAdminNotifications(Pageable pageable) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        return repository.findAll(pageable)
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
                            event.getCreatedAt()
                    );
                });
    }
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(
            UUID userId,
            String type,
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

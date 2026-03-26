package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.dto.NotificationResponse;
import com.icentric.Icentric.learning.entity.NotificationEvent;
import com.icentric.Icentric.learning.repository.NotificationRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icentric.Icentric.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    TenantSchemaService tenantSchemaService;

    @InjectMocks
    NotificationService notificationService;

    @Test
    @DisplayName("getNotifications: filters by type and maps response")
    void getNotifications_filtersByType() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        NotificationEvent event = new NotificationEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setType("REMINDER");
        event.setMessage("Due soon");
        event.setIsRead(false);
        event.setCreatedAt(createdAt);

        when(repository.findByUserIdAndType(userId, "REMINDER", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<NotificationResponse> result = notificationService.getNotifications(
                userId,
                "REMINDER",
                PageRequest.of(0, 10)
        );

        verify(tenantSchemaService).applyCurrentTenantSearchPath();
        assertThat(result.getContent()).singleElement().satisfies(notification -> {
            assertThat(notification.id()).isEqualTo(event.getId());
            assertThat(notification.type()).isEqualTo("REMINDER");
            assertThat(notification.message()).isEqualTo("Due soon");
            assertThat(notification.isRead()).isFalse();
            assertThat(notification.createdAt()).isEqualTo(createdAt);
        });
    }

    @Test
    @DisplayName("markAsRead: throws not found when notification does not exist")
    void markAsRead_notFound() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(repository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Notification not found");
    }

    @Test
    @DisplayName("markAsRead: throws access denied for another user's notification")
    void markAsRead_forbiddenForOtherUser() {
        UUID notificationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        NotificationEvent event = new NotificationEvent();
        event.setId(notificationId);
        event.setUserId(ownerId);
        event.setIsRead(false);

        when(repository.findById(notificationId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, requesterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Unauthorized access");

        verify(repository, never()).save(event);
    }

    @Test
    @DisplayName("markAllAsRead: delegates to repository bulk update")
    void markAllAsRead_delegatesToBulkUpdate() {
        UUID userId = UUID.randomUUID();

        notificationService.markAllAsRead(userId);

        verify(tenantSchemaService).applyCurrentTenantSearchPath();
        verify(repository).markAllAsRead(userId);
    }
}

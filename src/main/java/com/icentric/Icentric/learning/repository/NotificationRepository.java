package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.constants.NotificationType;
import com.icentric.Icentric.learning.entity.NotificationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository
        extends JpaRepository<NotificationEvent, UUID> {

    List<NotificationEvent> findBySentFalse();

    boolean existsByUserIdAndTypeAndSentFalse(UUID userId, NotificationType type);
    boolean existsByEventKey(String eventKey);
    boolean existsByUserIdAndTypeAndCreatedAtAfter(UUID userId, NotificationType type, java.time.Instant createdAt);
    boolean existsByUserIdAndTypeAndMessageAndCreatedAtAfter(UUID userId, NotificationType type, String message, java.time.Instant createdAt);
    Page<NotificationEvent> findAll(Pageable pageable);
    Page<NotificationEvent> findByUserId(UUID userId, Pageable pageable);

    Page<NotificationEvent> findByUserIdAndType(
            UUID userId,
            NotificationType type,
            Pageable pageable
    );

    long countByUserIdAndIsReadFalse(UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
UPDATE NotificationEvent n
SET n.isRead = true
WHERE n.userId = :userId
AND n.isRead = false
""")
    int markAllAsRead(@Param("userId") UUID userId);
}

package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.NotificationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository
        extends JpaRepository<NotificationEvent, UUID> {

    List<NotificationEvent> findBySentFalse();

    boolean existsByUserIdAndTypeAndSentFalse(UUID userId, String type);
    Page<NotificationEvent> findAll(Pageable pageable);
}

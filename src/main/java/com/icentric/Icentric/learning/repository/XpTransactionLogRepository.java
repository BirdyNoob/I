package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.XpTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface XpTransactionLogRepository extends JpaRepository<XpTransactionLog, UUID> {

    boolean existsByUserIdAndEventTypeAndReferenceEntityId(UUID userId, String eventType, UUID referenceEntityId);
}

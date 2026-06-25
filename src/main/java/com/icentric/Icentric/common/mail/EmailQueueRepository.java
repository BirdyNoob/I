package com.icentric.Icentric.common.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EmailQueueRepository extends JpaRepository<EmailQueueEntry, UUID> {

    @Query("SELECT e FROM EmailQueueEntry e WHERE e.status = 'PENDING' AND e.retryCount < 5 ORDER BY e.createdAt ASC LIMIT 50")
    List<EmailQueueEntry> findPendingBatch();
}

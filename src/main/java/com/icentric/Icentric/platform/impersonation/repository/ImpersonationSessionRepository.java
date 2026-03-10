package com.icentric.Icentric.platform.impersonation.repository;
import com.icentric.Icentric.platform.impersonation.entity.ImpersonationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface ImpersonationSessionRepository
        extends JpaRepository<ImpersonationSession, UUID> {
}
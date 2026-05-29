package com.icentric.Icentric.common.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateLimitLockRepository extends JpaRepository<RateLimitLock, String> {
}

package com.icentric.Icentric.platform.repository;

import com.icentric.Icentric.platform.entity.TenantStatsCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantStatsCacheRepository extends JpaRepository<TenantStatsCache, String> {
}

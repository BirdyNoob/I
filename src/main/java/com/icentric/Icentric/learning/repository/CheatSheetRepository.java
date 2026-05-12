package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.learning.entity.CheatSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CheatSheetRepository extends JpaRepository<CheatSheet, String> {

    // Legacy department-based (kept for backward compatibility)
    List<CheatSheet> findByDepartment(Department department);
    List<CheatSheet> findByDepartmentIsNullOrDepartment(Department department);

    /**
     * Returns cheat sheets that are:
     *   1. Global  (track_id IS NULL), OR
     *   2. Linked to any of the user's assigned track IDs
     *
     * Used by the learner API.
     */
    @Query("SELECT c FROM CheatSheet c WHERE c.trackId IS NULL OR c.trackId IN :trackIds")
    List<CheatSheet> findGlobalOrByTrackIds(@Param("trackIds") List<UUID> trackIds);

    /**
     * All cheat sheets for a specific track (used by admin per-track view).
     */
    List<CheatSheet> findByTrackId(UUID trackId);
}

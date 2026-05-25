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
     *   1. Global  (module_id IS NULL), OR
     *   2. Linked to any of the user's completed module IDs
     *
     * Used by the learner API.
     */
    @Query("SELECT c FROM CheatSheet c WHERE c.moduleId IS NULL OR c.moduleId IN :moduleIds")
    List<CheatSheet> findGlobalOrByModuleIds(@Param("moduleIds") List<UUID> moduleIds);

    /**
     * All cheat sheets for a specific module (used by admin per-module view).
     */
    List<CheatSheet> findByModuleId(UUID moduleId);
}

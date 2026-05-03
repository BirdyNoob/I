package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.learning.entity.CheatSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheatSheetRepository extends JpaRepository<CheatSheet, String> {
    List<CheatSheet> findByDepartment(Department department);
    List<CheatSheet> findByDepartmentIsNullOrDepartment(Department department);
}

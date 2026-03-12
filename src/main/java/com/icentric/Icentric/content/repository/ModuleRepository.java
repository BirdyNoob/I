package com.icentric.Icentric.content.repository;

import com.icentric.Icentric.content.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModuleRepository extends JpaRepository<CourseModule, UUID> {

    List<CourseModule> findByTrackId(UUID trackId);

}

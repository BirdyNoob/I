package com.icentric.Icentric.content.service;

import com.icentric.Icentric.content.dto.CreateModuleRequest;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.repository.ModuleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ModuleService {

    private final ModuleRepository repository;

    public ModuleService(ModuleRepository repository) {
        this.repository = repository;
    }

    public CourseModule createModule(UUID trackId, CreateModuleRequest request) {

        CourseModule module = new CourseModule();

        module.setId(UUID.randomUUID());
        module.setTrackId(trackId);
        module.setTitle(request.title());
        module.setSortOrder(request.sortOrder());
        module.setIsPublished(false);
        module.setCreatedAt(Instant.now());

        return repository.save(module);
    }
}

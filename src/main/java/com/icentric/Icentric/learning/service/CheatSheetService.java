package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.enums.Department;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.learning.entity.CheatSheet;
import com.icentric.Icentric.learning.repository.CheatSheetRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CheatSheetService {

    private final CheatSheetRepository repository;
    private final ObjectMapper objectMapper;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;

    public CheatSheetService(CheatSheetRepository repository, ObjectMapper objectMapper,
                             TenantUserRepository tenantUserRepository, TenantRepository tenantRepository) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public Map<String, Object> createOrUpdateCheatSheet(Map<String, Object> payload) {
        if (!payload.containsKey("title") || !payload.containsKey("type")) {
            throw new IllegalArgumentException("title and type are required");
        }

        String id = payload.containsKey("id") && payload.get("id") != null ? payload.get("id").toString() : UUID.randomUUID().toString();
        String title = payload.get("title").toString();
        String type = payload.get("type").toString();
        Department department = payload.containsKey("department") && payload.get("department") != null ? Department.fromString(payload.get("department").toString()) : null;
        String description = payload.containsKey("description") && payload.get("description") != null ? payload.get("description").toString() : null;

        // The remaining payload fields go into data
        Map<String, Object> dataMap = new java.util.HashMap<>(payload);
        dataMap.remove("id");
        dataMap.remove("title");
        dataMap.remove("type");
        dataMap.remove("department");
        dataMap.remove("description");

        JsonNode dataNode = objectMapper.valueToTree(dataMap);

        CheatSheet cheatSheet = repository.findById(id).orElse(new CheatSheet());
        cheatSheet.setId(id);
        cheatSheet.setTitle(title);
        cheatSheet.setType(type);
        cheatSheet.setDepartment(department);
        cheatSheet.setDescription(description);
        cheatSheet.setData(dataNode);

        CheatSheet saved = repository.save(cheatSheet);
        return formatCheatSheet(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllCheatSheets() {
        return repository.findAll().stream()
                .map(this::formatCheatSheet)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCheatSheetsForCurrentUser() {
        Department department = resolveCurrentUserDepartment();
        return repository.findByDepartmentIsNullOrDepartment(department).stream()
                .map(this::formatCheatSheet)
                .collect(Collectors.toList());
    }

    private Department resolveCurrentUserDepartment() {
        UUID userId = currentActorUserId();
        if (userId == null) return null;

        String slug = TenantContext.getTenant();
        if (slug == null) return null;

        return tenantRepository.findBySlug(slug)
                .flatMap(tenant -> tenantUserRepository.findByUserIdAndTenantId(userId, tenant.getId()))
                .map(TenantUser::getDepartment)
                .orElse(null);
    }

    private UUID currentActorUserId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getDetails().toString());
        } catch (IllegalArgumentException e) {
            return null; // Not a valid UUID format
        }
    }

    @Transactional
    public void deleteCheatSheet(String id) {
        repository.deleteById(id);
    }

    private Map<String, Object> formatCheatSheet(CheatSheet cheatSheet) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", cheatSheet.getId());
        response.put("title", cheatSheet.getTitle());
        response.put("type", cheatSheet.getType());
        
        if (cheatSheet.getDepartment() != null) {
            response.put("department", cheatSheet.getDepartment());
        }
        
        if (cheatSheet.getDescription() != null) {
            response.put("description", cheatSheet.getDescription());
        }

        if (cheatSheet.getUpdatedAt() != null) {
            response.put("updatedAt", cheatSheet.getUpdatedAt().toString());
        }

        JsonNode data = cheatSheet.getData();
        if (data != null && data.isObject()) {
            Map<String, Object> dataMap = objectMapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (dataMap != null) {
                response.putAll(dataMap);
            }
        }

        return response;
    }
}

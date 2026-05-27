package com.icentric.Icentric.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.entity.CheatSheet;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.CheatSheetRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.common.security.SecurityUtils;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CheatSheetService {

    private final CheatSheetRepository repository;
    private final ObjectMapper objectMapper;
    private final UserAssignmentRepository assignmentRepository;
    private final TenantSchemaService tenantSchemaService;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;

    public CheatSheetService(
            CheatSheetRepository repository,
            ObjectMapper objectMapper,
            UserAssignmentRepository assignmentRepository,
            TenantSchemaService tenantSchemaService,
            LessonRepository lessonRepository,
            LessonProgressRepository lessonProgressRepository
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.assignmentRepository = assignmentRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.lessonRepository = lessonRepository;
        this.lessonProgressRepository = lessonProgressRepository;
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createOrUpdateCheatSheet(Map<String, Object> payload) {
        if (!payload.containsKey("title") || !payload.containsKey("type")) {
            throw new IllegalArgumentException("title and type are required");
        }

        String id = payload.containsKey("id") && payload.get("id") != null
                ? payload.get("id").toString()
                : UUID.randomUUID().toString();

        String title = payload.get("title").toString();
        String type = payload.get("type").toString();
        String description = getStringOrNull(payload, "description");

        // moduleId — nullable; null = global cheat sheet visible to all learners
        UUID moduleId = null;
        if (payload.containsKey("moduleId") && payload.get("moduleId") != null) {
            try {
                moduleId = UUID.fromString(payload.get("moduleId").toString());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid moduleId UUID: " + payload.get("moduleId"));
            }
        }

        // Everything else goes into the JSONB data blob
        Map<String, Object> dataMap = new HashMap<>(payload);
        dataMap.remove("id");
        dataMap.remove("title");
        dataMap.remove("type");
        dataMap.remove("moduleId");
        dataMap.remove("description");

        JsonNode dataNode = objectMapper.valueToTree(dataMap);

        CheatSheet cheatSheet = repository.findById(id).orElse(new CheatSheet());
        cheatSheet.setId(id);
        cheatSheet.setTitle(title);
        cheatSheet.setType(type);
        cheatSheet.setModuleId(moduleId);
        cheatSheet.setDescription(description);
        cheatSheet.setData(dataNode);

        CheatSheet saved = repository.save(cheatSheet);
        return formatCheatSheet(saved);
    }

    @Transactional
    public List<Map<String, Object>> createOrUpdateCheatSheets(List<Map<String, Object>> payloads) {
        return payloads.stream()
                .map(this::createOrUpdateCheatSheet)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllCheatSheets() {
        return repository.findAll().stream()
                .map(this::formatCheatSheet)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCheatSheetsByModule(UUID moduleId) {
        return repository.findByModuleId(moduleId).stream()
                .map(this::formatCheatSheet)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteCheatSheet(String id) {
        repository.deleteById(id);
    }

    // ── LEARNER ───────────────────────────────────────────────────────────────

    /**
     * Returns cheat sheets visible to the currently authenticated learner:
     *   - Global sheets (trackId IS NULL), PLUS
     *   - Sheets linked to tracks the learner is currently assigned to.
     *
     * Resolves the user from SecurityContext → looks up their assignments
     * → extracts track IDs → queries the cheat sheet table.
     */
    @Transactional
    public List<Map<String, Object>> getCheatSheetsForCurrentUser() {
        // user_assignments is in the tenant schema — must set search_path first.
        // This @Transactional method satisfies the MANDATORY propagation requirement.
        tenantSchemaService.applyCurrentTenantSearchPath();

        UUID userId = currentActorUserId();
        if (userId == null) {
            // No authenticated user — return only global sheets
            return repository.findGlobalOrByModuleIds(Collections.emptyList()).stream()
                    .map(this::formatCheatSheet)
                    .collect(Collectors.toList());
        }

        // Fetch completed counts per module for this user
        List<Object[]> completedRows = lessonProgressRepository.countCompletedLessonsByModule(userId);
        Map<UUID, Long> completedMap = new HashMap<>();
        for (Object[] row : completedRows) {
            if (row[0] != null && row[1] != null) {
                completedMap.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        // Fetch total counts per module
        List<Object[]> totalRows = lessonRepository.countTotalLessonsByModule();
        Map<UUID, Long> totalMap = new HashMap<>();
        for (Object[] row : totalRows) {
            if (row[0] != null && row[1] != null) {
                totalMap.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        // Find module IDs that are 100% completed
        List<UUID> completedModuleIds = new ArrayList<>();
        totalMap.forEach((moduleId, totalCount) -> {
            long completedCount = completedMap.getOrDefault(moduleId, 0L);
            if (totalCount > 0 && completedCount >= totalCount) {
                completedModuleIds.add(moduleId);
            }
        });

        if (completedModuleIds.isEmpty()) {
            return repository.findGlobalOrByModuleIds(Collections.emptyList()).stream()
                    .map(this::formatCheatSheet)
                    .collect(Collectors.toList());
        }

        // Returns global (moduleId IS NULL) + module-specific sheets in one query
        return repository.findGlobalOrByModuleIds(completedModuleIds).stream()
                .map(this::formatCheatSheet)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID currentActorUserId() {
        return SecurityUtils.currentUserIdOrNull();
    }

    private String getStringOrNull(Map<String, Object> map, String key) {
        return map.containsKey(key) && map.get(key) != null
                ? map.get(key).toString()
                : null;
    }

    private Map<String, Object> formatCheatSheet(CheatSheet cs) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", cs.getId());
        response.put("title", cs.getTitle());
        response.put("type", cs.getType());

        if (cs.getModuleId() != null) {
            response.put("moduleId", cs.getModuleId());
        }
        if (cs.getDescription() != null) {
            response.put("description", cs.getDescription());
        }
        if (cs.getUpdatedAt() != null) {
            response.put("updatedAt", cs.getUpdatedAt().toString());
        }

        // Flatten JSONB data fields into the response map
        JsonNode data = cs.getData();
        if (data != null && data.isObject()) {
            Map<String, Object> dataMap = objectMapper.convertValue(
                    data, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (dataMap != null) {
                response.putAll(dataMap);
            }
        }

        return response;
    }
}

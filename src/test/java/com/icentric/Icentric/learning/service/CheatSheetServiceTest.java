package com.icentric.Icentric.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.entity.CheatSheet;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.CheatSheetRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CheatSheetServiceTest {

    @Mock
    private CheatSheetRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAssignmentRepository assignmentRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantSchemaService tenantSchemaService;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonProgressRepository lessonProgressRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CheatSheetService service;

    @BeforeEach
    void setUp() {
        service = new CheatSheetService(
                repository,
                objectMapper,
                userRepository,
                assignmentRepository,
                tenantRepository,
                tenantSchemaService,
                lessonRepository,
                lessonProgressRepository
        );
    }

    @Test
    void testCreateCheatSheet_withModuleId() {
        UUID moduleId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Test Cheatsheet");
        payload.put("type", "PHISHING");
        payload.put("moduleId", moduleId.toString());
        payload.put("description", "A test cheatsheet");

        CheatSheet mockSaved = new CheatSheet();
        mockSaved.setId(UUID.randomUUID().toString());
        mockSaved.setTitle("Test Cheatsheet");
        mockSaved.setType("PHISHING");
        mockSaved.setModuleId(moduleId);
        mockSaved.setDescription("A test cheatsheet");

        when(repository.save(any(CheatSheet.class))).thenReturn(mockSaved);

        Map<String, Object> result = service.createOrUpdateCheatSheet(payload);

        assertNotNull(result);
        assertEquals("Test Cheatsheet", result.get("title"));
        assertEquals("PHISHING", result.get("type"));
        assertEquals(moduleId, result.get("moduleId"));
    }

    @Test
    void testGetCheatSheetsForCurrentUser_withCompletedModules() {
        UUID userId = UUID.randomUUID();
        String userEmail = "learner@icentric.com";

        // Setup security context
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userEmail);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setEmail(userEmail);
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(mockUser));

        UUID activeModuleId = UUID.randomUUID();
        UUID incompleteModuleId = UUID.randomUUID();

        // 100% complete module setup: 3 completed out of 3 total
        List<Object[]> completedRows = new ArrayList<>();
        completedRows.add(new Object[]{activeModuleId, 3L});
        completedRows.add(new Object[]{incompleteModuleId, 1L});
        when(lessonProgressRepository.countCompletedLessonsByModule(userId)).thenReturn(completedRows);

        List<Object[]> totalRows = new ArrayList<>();
        totalRows.add(new Object[]{activeModuleId, 3L});
        totalRows.add(new Object[]{incompleteModuleId, 3L}); // incomplete because completed is only 1
        when(lessonRepository.countTotalLessonsByModule()).thenReturn(totalRows);

        CheatSheet mockSheet = new CheatSheet();
        mockSheet.setId("sheet-1");
        mockSheet.setTitle("Active Cheatsheet");
        mockSheet.setType("SECURITY");
        mockSheet.setModuleId(activeModuleId);

        when(repository.findGlobalOrByModuleIds(eq(List.of(activeModuleId)))).thenReturn(List.of(mockSheet));

        List<Map<String, Object>> result = service.getCheatSheetsForCurrentUser();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Active Cheatsheet", result.get(0).get("title"));
        assertEquals(activeModuleId, result.get(0).get("moduleId"));

        verify(repository).findGlobalOrByModuleIds(eq(List.of(activeModuleId)));
        SecurityContextHolder.clearContext();
    }
}

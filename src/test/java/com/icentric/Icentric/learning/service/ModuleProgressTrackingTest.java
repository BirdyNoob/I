package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.LessonStepRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.LearningPathResponse;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.entity.ModuleProgress;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.*;
import com.icentric.Icentric.tenant.TenantSchemaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleProgressTrackingTest {

    @Mock UserAssignmentRepository assignmentRepository;
    @Mock LessonProgressRepository progressRepository;
    @Mock TrackRepository trackRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonStepRepository lessonStepRepository;
    @Mock IssuedCertificateRepository issuedCertificateRepository;
    @Mock CertificateRepository certificateRepository;
    @Mock TenantSchemaService tenantSchemaService;
    @Mock UserRepository userRepository;
    @Mock ModuleProgressRepository moduleProgressRepository;

    @InjectMocks
    LearnerDashboardService learnerDashboardService;

    private UUID userId;
    private UUID trackId;
    private UUID moduleId;
    private UUID lessonId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        trackId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        lessonId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should return NOT_STARTED when module progress has not started yet")
    void shouldReturnNotStartedWhenNoProgressExists() {
        // Arrange
        UserAssignment assignment = new UserAssignment();
        assignment.setTrackId(trackId);
        assignment.setUserId(userId);

        Track track = new Track();
        track.setId(trackId);
        track.setTitle("AI Policy Track");
        track.setIsPublished(true);
        track.setVersion(1);

        CourseModule module = new CourseModule();
        module.setId(moduleId);
        module.setTitle("Module 2: AI Policies");
        module.setTrackId(trackId);

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setModuleId(moduleId);
        lesson.setTitle("AI Tools Policy");

        when(assignmentRepository.findByUserId(userId)).thenReturn(List.of(assignment));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(moduleRepository.findByTrackIdOrderBySortOrder(trackId)).thenReturn(List.of(module));
        when(lessonRepository.findByModuleIdOrderBySortOrder(moduleId)).thenReturn(List.of(lesson));
        when(lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lessonId)).thenReturn(List.of());
        when(moduleProgressRepository.findByUserId(userId)).thenReturn(List.of());
        when(progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lessonId, "COMPLETED")).thenReturn(false);

        // Act
        List<LearningPathResponse> response = learnerDashboardService.getLearningPaths(userId);

        // Assert
        assertThat(response).isNotEmpty();
        var timeline = response.get(0).timeline();
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).status()).isEqualTo("NOT_STARTED");
        assertThat(timeline.get(0).label()).isEqualTo("Not started");

        var modules = response.get(0).modules();
        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).status()).isEqualTo("NOT_STARTED");
        assertThat(modules.get(0).meta()).isEqualTo("Not started");
    }

    @Test
    @DisplayName("Should return IN_PROGRESS when module has started")
    void shouldReturnInProgressWhenModuleStarted() {
        // Arrange
        UserAssignment assignment = new UserAssignment();
        assignment.setTrackId(trackId);
        assignment.setUserId(userId);

        Track track = new Track();
        track.setId(trackId);
        track.setTitle("AI Policy Track");
        track.setIsPublished(true);
        track.setVersion(1);

        CourseModule module = new CourseModule();
        module.setId(moduleId);
        module.setTitle("Module 2: AI Policies");
        module.setTrackId(trackId);

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setModuleId(moduleId);
        lesson.setTitle("AI Tools Policy");

        ModuleProgress mp = new ModuleProgress();
        mp.setModuleId(moduleId);
        mp.setUserId(userId);
        mp.setStatus("IN_PROGRESS");

        when(assignmentRepository.findByUserId(userId)).thenReturn(List.of(assignment));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(moduleRepository.findByTrackIdOrderBySortOrder(trackId)).thenReturn(List.of(module));
        when(lessonRepository.findByModuleIdOrderBySortOrder(moduleId)).thenReturn(List.of(lesson));
        when(lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lessonId)).thenReturn(List.of());
        when(moduleProgressRepository.findByUserId(userId)).thenReturn(List.of(mp));
        when(progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lessonId, "COMPLETED")).thenReturn(false);

        // Act
        List<LearningPathResponse> response = learnerDashboardService.getLearningPaths(userId);

        // Assert
        assertThat(response).isNotEmpty();
        var timeline = response.get(0).timeline();
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).status()).isEqualTo("CURRENT");
        assertThat(timeline.get(0).label()).isEqualTo("In progress 0%");

        var modules = response.get(0).modules();
        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Should track module start time and spent seconds on touch")
    void shouldTrackModuleStartAndTimeOnTouch() {
        // Arrange
        com.icentric.Icentric.platform.tenant.service.TenantProvisioningService tenantProvisioningService = mock(com.icentric.Icentric.platform.tenant.service.TenantProvisioningService.class);
        com.icentric.Icentric.audit.service.AuditService auditService = mock(com.icentric.Icentric.audit.service.AuditService.class);
        com.icentric.Icentric.audit.service.AuditMetadataService auditMetadataService = mock(com.icentric.Icentric.audit.service.AuditMetadataService.class);
        EntityManager entityManager = mock(EntityManager.class);
        jakarta.persistence.Query nativeQuery = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);

        LessonProgressService lessonProgressService = new LessonProgressService(
                progressRepository,
                entityManager,
                tenantProvisioningService,
                assignmentRepository,
                lessonRepository,
                lessonStepRepository,
                moduleRepository,
                auditService,
                auditMetadataService,
                moduleProgressRepository
        );

        com.icentric.Icentric.tenant.TenantContext.setTenant("acme");

        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setModuleId(moduleId);
        lesson.setSortOrder(1);

        CourseModule module = new CourseModule();
        module.setId(moduleId);
        module.setTrackId(trackId);

        UserAssignment assignment = new UserAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTrackId(trackId);
        assignment.setUserId(userId);
        assignment.setStatus(com.icentric.Icentric.learning.constants.AssignmentStatus.ASSIGNED);

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.findByModuleIdOrderBySortOrder(moduleId)).thenReturn(List.of(lesson));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(assignmentRepository.findByUserIdAndTrackId(userId, trackId)).thenReturn(Optional.of(assignment));
        when(moduleProgressRepository.findByUserIdAndModuleId(userId, moduleId)).thenReturn(Optional.empty());
        when(progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lessonId, "COMPLETED")).thenReturn(true);

        com.icentric.Icentric.learning.dto.LessonProgressRequest request = new com.icentric.Icentric.learning.dto.LessonProgressRequest(lessonId, "COMPLETED");

        // Act
        lessonProgressService.updateProgress(userId, request);

        // Assert
        ArgumentCaptor<ModuleProgress> progressCaptor = ArgumentCaptor.forClass(ModuleProgress.class);
        verify(moduleProgressRepository).save(progressCaptor.capture());
        ModuleProgress captured = progressCaptor.getValue();
        assertThat(captured.getModuleId()).isEqualTo(moduleId);
        assertThat(captured.getUserId()).isEqualTo(userId);
        assertThat(captured.getStatus()).isEqualTo("COMPLETED");
        assertThat(captured.getStartedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(captured.getSpentSeconds()).isEqualTo(0);
    }
}

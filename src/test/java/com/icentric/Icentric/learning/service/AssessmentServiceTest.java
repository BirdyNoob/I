package com.icentric.Icentric.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.LessonStepRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.assessment.AssessmentRenderResponse;
import com.icentric.Icentric.learning.entity.AssessmentAttempt;
import com.icentric.Icentric.learning.entity.AssessmentConfig;
import com.icentric.Icentric.learning.repository.*;
import com.icentric.Icentric.learning.service.impl.AssessmentServiceImpl;
import com.icentric.Icentric.tenant.TenantSchemaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock AssessmentConfigRepository assessmentConfigRepository;
    @Mock AssessmentAttemptRepository assessmentAttemptRepository;
    @Mock AssessmentAssignmentRepository assessmentAssignmentRepository;
    @Mock UserAssignmentRepository userAssignmentRepository;
    @Mock TrackRepository trackRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonStepRepository lessonStepRepository;
    @Mock LessonProgressRepository lessonProgressRepository;
    @Mock IssuedCertificateRepository issuedCertificateRepository;
    @Mock UserRepository userRepository;
    @Mock CertificateService certificateService;
    @Mock ObjectMapper objectMapper;
    @Mock TenantSchemaService tenantSchemaService;
    @Mock EntityManager entityManager;
    @Mock AuditService auditService;

    @InjectMocks
    AssessmentServiceImpl assessmentService;

    @Test
    @DisplayName("getAssessmentForRender logs ASSESSMENT_START when starting a new attempt")
    void getAssessmentForRender_logsStartOnNewAttempt() {
        UUID userId = UUID.randomUUID();
        String assessmentId = UUID.randomUUID().toString();
        UUID trackId = UUID.randomUUID();

        AssessmentConfig config = new AssessmentConfig();
        config.setId(assessmentId);
        config.setTrackId(trackId.toString());

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode configNode = mapper.createObjectNode();
        configNode.put("title", "Safety Test");
        configNode.put("trackName", "Safety Track");
        ObjectNode configSub = mapper.createObjectNode();
        configSub.put("totalQuestions", 5);
        configSub.put("timeLimitSeconds", 300);
        configSub.put("passingScore", 80);
        configSub.put("retakePolicy", "UNLIMITED");
        configNode.set("config", configSub);

        config.setConfigData(configNode);

        when(assessmentConfigRepository.findById(assessmentId)).thenReturn(Optional.of(config));
        when(assessmentAttemptRepository.findByUserIdAndAssessmentConfigId(userId, assessmentId))
                .thenReturn(new ArrayList<>());

        // Mock unlocking
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        when(moduleRepository.findByTrackIdOrderBySortOrder(trackId)).thenReturn(List.of(module));

        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        when(lessonRepository.findByModuleIdOrderBySortOrder(module.getId())).thenReturn(List.of(lesson));

        when(lessonProgressRepository.existsByUserIdAndLessonIdAndStatus(userId, lesson.getId(), "COMPLETED"))
                .thenReturn(true);

        AssessmentRenderResponse response = assessmentService.getAssessmentForRender(assessmentId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getAssessment().getAttemptId()).isNotNull();

        verify(auditService).log(
                eq(userId),
                eq(AuditAction.ASSESSMENT_START),
                eq("ASSESSMENT"),
                eq(assessmentId),
                contains("Started final assessment: Safety Test")
        );
        verify(assessmentAttemptRepository).save(any(AssessmentAttempt.class));
    }
}

package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    UserAssignmentRepository repository;
    @Mock
    TrackRepository trackRepository;
    @Mock
    AuditService auditService;
    @Mock
    UserRepository userRepository;
    @Mock
    TenantUserRepository tenantUserRepository;
    @Mock
    TenantRepository tenantRepository;
    @Mock
    TenantSchemaService tenantSchemaService;

    @InjectMocks
    AssignmentService assignmentService;

    @Test
    @DisplayName("bulkAssign applies tenant schema before repository lookup")
    void bulkAssign_appliesTenantSchemaBeforeAssignmentLookup() {
        UUID userId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Track track = new Track();
        track.setId(trackId);
        track.setVersion(3);

        when(userRepository.findByIdIn(List.of(userId))).thenReturn(List.of(user));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(repository.findByUserIdAndTrackId(userId, trackId)).thenReturn(Optional.empty());

        assignmentService.bulkAssign(new BulkAssignmentRequest(
                trackId,
                List.of(userId),
                null,
                Instant.now().plusSeconds(3600)
        ));

        InOrder inOrder = inOrder(tenantSchemaService, repository);
        inOrder.verify(tenantSchemaService).applyCurrentTenantSearchPath();
        inOrder.verify(repository).findByUserIdAndTrackId(userId, trackId);

        verifyNoMoreInteractions(auditService);
    }
}

package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.learning.dto.ReminderConfigRequest;
import com.icentric.Icentric.learning.dto.ReminderConfigResponse;
import com.icentric.Icentric.learning.entity.AssignmentNotificationConfig;
import com.icentric.Icentric.learning.repository.AssignmentNotificationConfigRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReminderConfigService {
    private static final List<Integer> DEFAULT_REMINDER_OFFSETS = List.of(48, 24);
    private static final int DEFAULT_ESCALATION_DELAY_HOURS = 24;

    private final AssignmentNotificationConfigRepository repository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public ReminderConfigService(
            AssignmentNotificationConfigRepository repository,
            TenantRepository tenantRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    @Transactional(readOnly = true)
    public ReminderConfigResponse getCurrentConfig() {
        Tenant tenant = currentTenant();
        ReminderSettings settings = resolveConfig(tenant.getId());
        return new ReminderConfigResponse(
                tenant.getId(),
                settings.reminderEnabled(),
                settings.reminderOffsetsHours(),
                settings.escalationEnabled(),
                settings.escalationDelayHours(),
                Instant.now()
        );
    }

    @Transactional
    public ReminderConfigResponse saveCurrentConfig(ReminderConfigRequest request) {
        Tenant tenant = currentTenant();
        AssignmentNotificationConfig config = repository.findByTenantId(tenant.getId())
                .orElseGet(() -> {
                    AssignmentNotificationConfig created = new AssignmentNotificationConfig();
                    created.setId(UUID.randomUUID());
                    created.setTenantId(tenant.getId());
                    created.setCreatedAt(Instant.now());
                    return created;
                });

        config.setReminderEnabled(request.reminderEnabled());
        config.setReminderOffsetsHours(toCsv(normalizeOffsets(request.reminderOffsetsHours())));
        config.setEscalationEnabled(request.escalationEnabled());
        config.setEscalationDelayHours(request.escalationDelayHours());
        config.setUpdatedAt(Instant.now());
        repository.save(config);

        UUID actorId = currentActorUserId();
        if (actorId != null) {
            auditService.log(
                    actorId,
                    AuditAction.UPDATE_TRACK,
                    "REMINDER_CONFIG",
                    config.getId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(actorId)
                            + " updated reminder config for " + auditMetadataService.currentTenantLabel()
                            + " offsets=" + normalizeOffsets(request.reminderOffsetsHours())
                            + ", escalationDelayHours=" + request.escalationDelayHours()
            );
        }

        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public ReminderSettings resolveConfig(UUID tenantId) {
        AssignmentNotificationConfig config = repository.findByTenantId(tenantId).orElse(null);
        if (config == null) {
            return new ReminderSettings(true, DEFAULT_REMINDER_OFFSETS, true, DEFAULT_ESCALATION_DELAY_HOURS);
        }
        return new ReminderSettings(
                Boolean.TRUE.equals(config.getReminderEnabled()),
                parseOffsets(config.getReminderOffsetsHours()),
                Boolean.TRUE.equals(config.getEscalationEnabled()),
                config.getEscalationDelayHours() != null ? config.getEscalationDelayHours() : DEFAULT_ESCALATION_DELAY_HOURS
        );
    }

    private ReminderConfigResponse toResponse(AssignmentNotificationConfig config) {
        return new ReminderConfigResponse(
                config.getTenantId(),
                Boolean.TRUE.equals(config.getReminderEnabled()),
                parseOffsets(config.getReminderOffsetsHours()),
                Boolean.TRUE.equals(config.getEscalationEnabled()),
                config.getEscalationDelayHours() != null ? config.getEscalationDelayHours() : DEFAULT_ESCALATION_DELAY_HOURS,
                config.getUpdatedAt()
        );
    }

    private Tenant currentTenant() {
        String slug = TenantContext.getTenant();
        if (slug == null || slug.isBlank()) {
            throw new IllegalStateException("Missing tenant in request context");
        }
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + slug));
    }

    private UUID currentActorUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        return userIdRaw == null ? null : UUID.fromString(userIdRaw.toString());
    }

    private List<Integer> normalizeOffsets(List<Integer> offsets) {
        return offsets.stream()
                .filter(offset -> offset != null && offset > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private List<Integer> parseOffsets(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_REMINDER_OFFSETS;
        }
        return normalizeOffsets(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .collect(Collectors.toList()));
    }

    private String toCsv(List<Integer> offsets) {
        return offsets.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public record ReminderSettings(
            boolean reminderEnabled,
            List<Integer> reminderOffsetsHours,
            boolean escalationEnabled,
            int escalationDelayHours
    ) {
    }
}

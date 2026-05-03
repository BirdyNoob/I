package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.ReportRow;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.icentric.Icentric.tenant.TenantSchemaService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {

    private final UserAssignmentRepository repository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final TenantSchemaService tenantSchemaService;
    private final com.icentric.Icentric.platform.tenant.repository.TenantRepository tenantRepository;

    public ReportService(
            UserAssignmentRepository repository,
            LessonRepository lessonRepository,
            LessonProgressRepository lessonProgressRepository,
            TenantSchemaService tenantSchemaService,
            com.icentric.Icentric.platform.tenant.repository.TenantRepository tenantRepository
    ) {
        this.repository = repository;
        this.lessonRepository = lessonRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.tenantRepository = tenantRepository;
    }

    private UUID getCurrentTenantId() {
        String slug = com.icentric.Icentric.tenant.TenantContext.getTenant();
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug))
                .getId();
    }

    @Transactional(readOnly = true)
    public List<ReportRow> getReportData(
            Department department,
            UUID trackId,
            List<AssignmentStatus> statuses
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        UUID currentTenantId = getCurrentTenantId();
        List<AssignmentStatus> normalizedStatuses = statuses == null
                ? List.of()
                : statuses.stream().distinct().toList();
        List<Object[]> data = loadReportData(currentTenantId, department, trackId, normalizedStatuses);
        return toReportRows(data);
    }

    private List<Object[]> loadReportData(
            UUID tenantId,
            Department department,
            UUID trackId,
            List<AssignmentStatus> statuses
    ) {
        if (statuses == null || statuses.isEmpty()) {
            return repository.fetchCompletionData(tenantId, department, null, trackId);
        }
        if (statuses.size() == 1) {
            return repository.fetchCompletionData(tenantId, department, statuses.get(0), trackId);
        }
        return repository.fetchReportDataByStatuses(tenantId, statuses, department, trackId);
    }

    private List<ReportRow> toReportRows(List<Object[]> data) {
        List<ReportRow> rows = new ArrayList<>();
        if (data.isEmpty()) {
            return rows;
        }

        Set<UUID> trackIds = new LinkedHashSet<>();
        Set<UUID> userIds = new LinkedHashSet<>();
        for (Object[] row : data) {
            UserAssignment ua = (UserAssignment) row[0];
            trackIds.add(ua.getTrackId());
            userIds.add(ua.getUserId());
        }

        Map<UUID, Long> totalLessonsByTrack = new HashMap<>();
        lessonRepository.countLessonsInTracks(trackIds).forEach(tuple -> totalLessonsByTrack.put((UUID) tuple[0], (Long) tuple[1]));

        Map<String, Long> completedByUserTrack = new HashMap<>();
        lessonProgressRepository.countCompletedLessonsByUserAndTrack(userIds, trackIds)
                .forEach(tuple -> completedByUserTrack.put(progressKey((UUID) tuple[0], (UUID) tuple[1]), (Long) tuple[2]));

        for (Object[] row : data) {
            UserAssignment ua = (UserAssignment) row[0];
            String learnerName = (String) row[1];
            String userEmail = (String) row[2];
            String role = (String) row[3];
            String department = "UNKNOWN";
            if (row[4] instanceof Department d) {
                department = d.getDisplayName();
            } else if (row[4] != null) {
                department = row[4].toString();
            }
            String courseName = (String) row[5];

            long totalLessons = totalLessonsByTrack.getOrDefault(ua.getTrackId(), 0L);
            long completedLessons = completedByUserTrack.getOrDefault(progressKey(ua.getUserId(), ua.getTrackId()), 0L);
            int completionPercent = totalLessons == 0 ? 0 : (int) ((completedLessons * 100) / totalLessons);

            Long daysToDeadline = null;
            if (ua.getDueDate() != null) {
                long secondsLeft = ua.getDueDate().getEpochSecond() - Instant.now().getEpochSecond();
                daysToDeadline = Math.floorDiv(secondsLeft, 86_400);
            }

            rows.add(new ReportRow(
                    ua.getUserId(),
                    learnerName,
                    userEmail,
                    role,
                    department,
                    ua.getTrackId().toString(),
                    courseName,
                    ua.getStatus().name(),
                    ua.getAssignedAt(),
                    ua.getDueDate(),
                    daysToDeadline,
                    (int) totalLessons,
                    (int) completedLessons,
                    completionPercent,
                    ua.getContentVersionAtAssignment(),
                    ua.getRequiresRetraining()
            ));
        }
        return rows;
    }

    public String toCsv(List<ReportRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("UserId,LearnerName,Email,Role,Department,TrackId,CourseName,Status,AssignedAt,DueDate,DaysToDeadline,TotalLessons,CompletedLessons,CompletionPercent,ContentVersionAtAssignment,RequiresRetraining\n");
        for (ReportRow row : rows) {
            appendCsvValue(builder, row.userId() == null ? null : row.userId().toString());
            builder.append(",");
            appendCsvValue(builder, row.learnerName());
            builder.append(",");
            appendCsvValue(builder, row.userEmail());
            builder.append(",");
            appendCsvValue(builder, row.role());
            builder.append(",");
            appendCsvValue(builder, row.department());
            builder.append(",");
            appendCsvValue(builder, row.trackId());
            builder.append(",");
            appendCsvValue(builder, row.courseName());
            builder.append(",");
            appendCsvValue(builder, row.status());
            builder.append(",");
            appendCsvValue(builder, row.assignedAt() == null ? null : row.assignedAt().toString());
            builder.append(",");
            appendCsvValue(builder, row.dueDate() == null ? null : row.dueDate().toString());
            builder.append(",");
            appendCsvValue(builder, row.daysToDeadline() == null ? null : row.daysToDeadline().toString());
            builder.append(",");
            appendCsvValue(builder, row.totalLessons() == null ? null : row.totalLessons().toString());
            builder.append(",");
            appendCsvValue(builder, row.completedLessons() == null ? null : row.completedLessons().toString());
            builder.append(",");
            appendCsvValue(builder, row.completionPercent() == null ? null : row.completionPercent().toString());
            builder.append(",");
            appendCsvValue(builder, row.contentVersionAtAssignment() == null ? null : row.contentVersionAtAssignment().toString());
            builder.append(",");
            appendCsvValue(builder, row.requiresRetraining() == null ? null : row.requiresRetraining().toString());
            builder.append("\n");
        }
        return builder.toString();
    }

    private String progressKey(UUID userId, UUID trackId) {
        return userId + ":" + trackId;
    }

    private void appendCsvValue(StringBuilder builder, String value) {
        builder.append(csvSafe(value));
    }

    private String csvSafe(String value) {

        if (value == null) {
            return "\"\"";
        }

        String sanitized = value
                .replace("\r", " ")
                .replace("\n", " ");

        if (startsWithFormulaTrigger(sanitized)) {
            sanitized = "'" + sanitized;
        }

        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    private boolean startsWithFormulaTrigger(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!Character.isWhitespace(current)) {
                return current == '=' ||
                        current == '+' ||
                        current == '-' ||
                        current == '@';
            }
        }

        return false;
    }
}

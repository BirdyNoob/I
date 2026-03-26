package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private final UserAssignmentRepository repository;
    private final TenantSchemaService tenantSchemaService;

    public ReportService(
            UserAssignmentRepository repository,
            TenantSchemaService tenantSchemaService
    ) {
        this.repository = repository;
        this.tenantSchemaService = tenantSchemaService;
    }

    // ✅ Risk Report
    @Transactional(readOnly = true)
    public StreamingResponseBody streamRiskReport(
            String department,
            UUID trackId
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        List<String[]> rows = new ArrayList<>();
        var data = repository.fetchRiskData(
                List.of(AssignmentStatus.FAILED, AssignmentStatus.OVERDUE),
                department,
                trackId
        );

        for (Object[] row : data) {
            UserAssignment ua = (UserAssignment) row[0];
            rows.add(new String[] {
                    (String) row[1],
                    ua.getTrackId().toString(),
                    ua.getStatus().name(),
                    (String) row[2]
            });
        }

        return outputStream -> {

            BufferedWriter writer =
                    new BufferedWriter(new OutputStreamWriter(outputStream));

            // header
            writer.write("Email,TrackId,Status,Department\n");

            for (String[] row : rows) {
                writeRow(writer, row);
            }

            writer.flush();
        };
    }
    @Transactional(readOnly = true)
    public StreamingResponseBody streamCompletionReport(
            String department,
            AssignmentStatus status,
            UUID trackId
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        List<String[]> rows = new ArrayList<>();
        var data = repository.fetchCompletionData(department, status, trackId);

        for (Object[] row : data) {
            UserAssignment ua = (UserAssignment) row[0];
            rows.add(new String[] {
                    (String) row[1],
                    ua.getTrackId().toString(),
                    ua.getStatus().name(),
                    (String) row[2]
            });
        }

        return outputStream -> {

            var writer = new BufferedWriter(new OutputStreamWriter(outputStream));

            // header
            writer.write("Email,TrackId,Status,Department\n");

            for (String[] row : rows) {
                writeRow(writer, row);
            }

            writer.flush();
        };
    }
    private void writeRow(BufferedWriter writer, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(",");
            }
            writer.write(csvSafe(values[i]));
        }
        writer.write("\n");
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

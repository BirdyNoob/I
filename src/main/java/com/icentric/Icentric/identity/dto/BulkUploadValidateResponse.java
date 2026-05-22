package com.icentric.Icentric.identity.dto;

import java.util.List;

public record BulkUploadValidateResponse(
        int totalRows,
        int validRowsCount,
        int invalidRowsCount,
        List<CsvRowValidationResult> rows
) {}

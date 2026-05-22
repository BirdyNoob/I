package com.icentric.Icentric.identity.dto;

import java.util.List;

public record CsvRowValidationResult(
        long rowNumber,
        String name,
        String email,
        String role,
        String department,
        boolean valid,
        List<String> errors
) {}

package com.icentric.Icentric.identity.dto;

import java.util.List;

public record BulkUploadResponse(

        int total,
        int success,
        int failed,
        List<String> errors

) {}

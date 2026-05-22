package com.icentric.Icentric.identity.dto;

import java.util.List;

public record BulkUploadConfirmRequest(
        List<BulkUploadRowDto> users,
        Boolean autoAssignTracks
) {}

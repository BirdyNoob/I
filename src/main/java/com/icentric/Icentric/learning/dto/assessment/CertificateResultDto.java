package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CertificateResultDto {
    private String certificateId;
    private String displayId;
    private String recipientName;
    private String trackName;
    private String issuedDate;
    private String downloadUrl;
}

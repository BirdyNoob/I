package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SubmitAssessmentResponse {
    private AssessmentResultDto result;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CertificateResultDto certificate;
    
    private List<WeakAreaDto> weakAreas;
}

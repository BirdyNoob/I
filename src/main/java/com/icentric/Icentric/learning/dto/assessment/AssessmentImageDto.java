package com.icentric.Icentric.learning.dto.assessment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentImageDto {
    private String imageId;
    private String fileName;
    private String mimeType;
    private String altText;
    private String data;
}

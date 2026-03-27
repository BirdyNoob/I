package com.icentric.Icentric.learning.dto;

public record CertificateDownloadResult(
        String filename,
        byte[] pdf
) {}

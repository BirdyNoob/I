package com.icentric.Icentric.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(name = "app.certificate-storage", havingValue = "s3")
public class S3CertificateStorageService implements CertificateStorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    public S3CertificateStorageService(
            S3Client s3Client,
            @Value("${app.s3.certificate-bucket}") String bucketName,
            @Value("${app.s3.certificate-prefix:certificates/}") String prefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    @Override
    public String store(String key, byte[] content) {
        String objectKey = prefix + key;
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType("application/pdf")
                        .build(),
                RequestBody.fromBytes(content)
        );
        return objectKey;
    }

    @Override
    public byte[] load(String key) {
        String objectKey = key.startsWith(prefix) ? key : prefix + key;
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build()
        ).asByteArray();
    }
}

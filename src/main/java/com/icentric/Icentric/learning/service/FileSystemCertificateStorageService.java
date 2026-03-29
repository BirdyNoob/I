package com.icentric.Icentric.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileSystemCertificateStorageService implements CertificateStorageService {

    private final Path rootPath;

    public FileSystemCertificateStorageService(
            @Value("${app.certificate.storage-root:/tmp/icentric-certificates}") String storageRoot
    ) {
        this.rootPath = Path.of(storageRoot);
    }

    @Override
    public String store(String key, byte[] content) {
        try {
            Path target = rootPath.resolve(key).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store certificate blob for key: " + key, e);
        }
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(rootPath.resolve(key).normalize());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load certificate blob for key: " + key, e);
        }
    }
}

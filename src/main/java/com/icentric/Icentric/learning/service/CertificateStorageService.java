package com.icentric.Icentric.learning.service;

public interface CertificateStorageService {

    String store(String key, byte[] content);

    byte[] load(String key);
}

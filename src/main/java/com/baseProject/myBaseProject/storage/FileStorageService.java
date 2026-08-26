package com.baseProject.myBaseProject.storage;

import java.time.Instant;

public interface FileStorageService {

    void upload(String key, byte[] content, String contentType);
    byte[] download(String key);
    PresignedUrl presignGet(String key);
    void delete(String key);

    record PresignedUrl(String url, Instant expiresAt) {
    }
}

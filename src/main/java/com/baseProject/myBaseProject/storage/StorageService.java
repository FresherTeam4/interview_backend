package com.baseProject.myBaseProject.storage;

import java.time.Duration;

public interface StorageService {

    void upload(String key, byte[] bytes, String contentType);

    byte[] download(String key);

    void delete(String key);

    boolean exists(String key);

    String generatePresignedUrl(String key, Duration duration);
}

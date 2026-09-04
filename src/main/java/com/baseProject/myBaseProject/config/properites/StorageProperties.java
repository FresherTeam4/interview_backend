package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank(message = "Storage endpoint must not be blank")
        String endpoint,

        @NotBlank(message = "Storage accessKey must not be blank")
        String accessKey,

        @NotBlank(message = "Storage secretKey must not be blank")
        String secretKey,

        @NotBlank(message = "Storage bucketName must not be blank")
        String bucketName,

        String region
) {
    public StorageProperties {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
    }
}

package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank(message = "Thiếu app.storage.endpoint")
        String endpoint,

        @NotBlank(message = "Thiếu app.storage.public-endpoint")
        String publicEndpoint,

        @Pattern(
                regexp = "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]",
                message = "app.storage.bucket chỉ nhận chữ thường, số và dấu gạch ngang, dài 3–63 ký tự"
        )
        String bucket,

        @NotBlank(message = "Thiếu app.storage.region")
        String region,

        @NotBlank(message = "Thiếu biến môi trường STORAGE_ACCESS_KEY")
        String accessKey,

        @NotBlank(message = "Thiếu biến môi trường STORAGE_SECRET_KEY")
        String secretKey,

        @Positive(message = "app.storage.presign-ttl-seconds phải là số dương")
        @Max(value = 604800, message = "SigV4 không ký được link sống quá 7 ngày (604800 giây)")
        long presignTtlSeconds
) {
}

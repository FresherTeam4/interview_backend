package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kết nối tới object storage. Local là MinIO qua docker compose, deploy thật có thể là
 * S3 hoặc bất cứ cái gì nói cùng giao thức — nên ở đây không có chữ "minio" nào.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        /** Địa chỉ server gọi vào. Trong docker network thường là http://minio:9000. */
        @NotBlank(message = "Thiếu app.storage.endpoint")
        String endpoint,

        /**
         * Địa chỉ trình duyệt người dùng mở được. Tách khỏi {@code endpoint} vì SigV4 ký
         * cả hostname: link ký cho "minio:9000" đưa ra ngoài là link không mở được.
         */
        @NotBlank(message = "Thiếu app.storage.public-endpoint")
        String publicEndpoint,

        /**
         * Chữ, số và dấu gạch ngang, 3–63 ký tự. Sai định dạng thì phải chết ngay lúc
         * khởi động: cả path-style URL lẫn link presigned đều vỡ ở tên bucket không hợp lệ.
         */
        @Pattern(
                regexp = "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]",
                message = "app.storage.bucket chỉ nhận chữ thường, số và dấu gạch ngang, dài 3–63 ký tự"
        )
        String bucket,

        /** MinIO không có region thật, nhưng SigV4 vẫn đòi một giá trị để ký. */
        @NotBlank(message = "Thiếu app.storage.region")
        String region,

        @NotBlank(message = "Thiếu biến môi trường STORAGE_ACCESS_KEY")
        String accessKey,

        @NotBlank(message = "Thiếu biến môi trường STORAGE_SECRET_KEY")
        String secretKey,

        /** Tuổi của link tải. Trần 7 ngày là giới hạn cứng của SigV4, không phải tôi chọn. */
        @Positive(message = "app.storage.presign-ttl-seconds phải là số dương")
        @Max(value = 604800, message = "SigV4 không ký được link sống quá 7 ngày (604800 giây)")
        long presignTtlSeconds
) {
}

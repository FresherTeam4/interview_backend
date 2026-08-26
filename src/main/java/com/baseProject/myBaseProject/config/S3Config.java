package com.baseProject.myBaseProject.config;

import com.baseProject.myBaseProject.config.properites.StorageProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Hai bean nói chuyện với MinIO: một để đọc/ghi file, một để tạo link tải tạm thời.
 *
 * <p>Dùng AWS SDK v2 chứ không phải client riêng của MinIO vì MinIO nói đúng giao thức S3;
 * đổi sang S3 thật sau này chỉ là đổi 4 dòng cấu hình trong {@code application.yaml}
 * (mục 11.1 của {@code docs/cv-profile-api.md}).
 *
 * <p>Không đặt {@code @ConditionalOnProperty}: thiếu cấu hình storage thì app phải chết ngay lúc
 * khởi động, chứ không phải chết lúc người dùng đầu tiên bấm upload.
 */
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final StorageProperties storageProperties;

    /**
     * Client dùng cho mọi lệnh đọc/ghi từ chính backend.
     *
     * <p>Trỏ vào {@code app.storage.endpoint} — địa chỉ backend gọi được. Trong Docker Compose
     * đó là {@code http://minio:9000} (tên service trong mạng nội bộ), người ngoài không truy cập
     * được địa chỉ này.
     *
     * <p>Spring tự gọi {@code close()} khi tắt context vì {@code S3Client} có sẵn phương thức đó
     * (suy luận destroy method mặc định), nên không cần khai báo gì thêm.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(storageProperties.endpoint()))
                .region(Region.of(storageProperties.region()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(pathStyleConfiguration())
                .build();
    }

    /**
     * Bộ tạo link tải có hạn — trỏ vào {@code public-endpoint}, <strong>không</strong> phải
     * {@code endpoint}.
     *
     * <p>Chữ ký SigV4 ký cả tên host: link ký bằng {@code http://minio:9000} thì trình duyệt của
     * người dùng vừa không phân giải được tên đó, vừa làm sai chữ ký nếu có proxy đổi host.
     * Vì vậy hai bean cố tình dùng hai địa chỉ khác nhau, dù cùng một bucket và cùng một khóa.
     *
     * <p>{@code S3Presigner} chỉ tính toán chữ ký tại chỗ, không gọi mạng, nên bean này vẫn tạo
     * được ngay cả khi MinIO chưa chạy.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(storageProperties.publicEndpoint()))
                .region(Region.of(storageProperties.region()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(pathStyleConfiguration())
                .build();
    }

    /**
     * Khóa tĩnh lấy từ cấu hình.
     *
     * <p>Không dùng chuỗi provider mặc định của AWS: nó sẽ lần lượt dò biến môi trường, file
     * {@code ~/.aws/credentials}, rồi metadata endpoint của EC2 — thừa với MinIO và gây một
     * khoảng treo vài giây lúc khởi động trên máy không phải AWS.
     */
    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                storageProperties.accessKey(), storageProperties.secretKey()));
    }

    /**
     * {@code http://host/bucket/key} thay vì {@code http://bucket.host/key}.
     *
     * <p>Kiểu virtual-host mặc định của S3 cần DNS wildcard cho từng bucket — MinIO chạy local
     * không có, nên bắt buộc bật path style.
     */
    private S3Configuration pathStyleConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
    }
}

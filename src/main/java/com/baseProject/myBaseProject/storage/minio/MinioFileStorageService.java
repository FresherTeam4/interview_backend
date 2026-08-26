package com.baseProject.myBaseProject.storage.minio;

import com.baseProject.myBaseProject.config.properites.StorageProperties;
import com.baseProject.myBaseProject.exception.StorageUnavailableException;
import com.baseProject.myBaseProject.storage.FileStorageService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private static final int HTTP_NOT_FOUND = 404;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @PostConstruct
    void ensureBucketExists() {
        String bucket = storageProperties.bucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket '{}' đã có sẵn", bucket);
        } catch (NoSuchBucketException e) {
            createBucket(bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == HTTP_NOT_FOUND) {
                createBucket(bucket);
            } else {
                log.warn("Không kiểm tra được bucket '{}': {}", bucket, e.getMessage());
            }
        } catch (SdkException e) {
            log.warn("Chưa kết nối được tới storage tại {} để kiểm tra bucket '{}': {}",
                    storageProperties.endpoint(), bucket, e.getMessage());
        }
    }

    private void createBucket(String bucket) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Đã tạo bucket '{}' (private theo mặc định của giao thức S3)", bucket);
        } catch (SdkException e) {
            log.warn("Không tạo được bucket '{}': {}", bucket, e.getMessage());
        }
    }

    @Override
    public void upload(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(storageProperties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
            log.debug("Đã ghi {} byte vào object storage", content.length);
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public PresignedUrl presignGet(String key) {
        try {
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(storageProperties.presignTtlSeconds()))
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(storageProperties.bucket())
                                    .key(key)
                                    .build())
                            .build());

            return new PresignedUrl(presigned.url().toString(), presigned.expiration());
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }
}

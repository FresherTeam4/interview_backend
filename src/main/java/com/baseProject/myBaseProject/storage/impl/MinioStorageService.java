package com.baseProject.myBaseProject.storage.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.baseProject.myBaseProject.config.properites.StorageProperties;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.storage.StorageService;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
            log.info("Uploaded file to MinIO bucket '{}' with key '{}'", properties.bucketName(), key);
        } catch (Exception e) {
            log.error("Failed to upload file to storage with key '{}': {}", key, e.getMessage(), e);
            throw new DomainException(ErrorCode.STORAGE_ERROR, "Không thể upload file lên hệ thống lưu trữ: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(key)
                    .build();

            return s3Client.getObjectAsBytes(getRequest).asByteArray();
        } catch (NoSuchKeyException e) {
            log.warn("File not found in storage for key '{}'", key);
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy file trên hệ thống lưu trữ: " + key, e);
        } catch (Exception e) {
            log.error("Failed to download file with key '{}': {}", key, e.getMessage(), e);
            throw new DomainException(ErrorCode.STORAGE_ERROR, "Lỗi khi tải file từ hệ thống lưu trữ: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Deleted file with key '{}' from bucket '{}'", key, properties.bucketName());
        } catch (Exception e) {
            log.error("Failed to delete file with key '{}': {}", key, e.getMessage(), e);
            throw new DomainException(ErrorCode.STORAGE_ERROR, "Lỗi khi xóa file khỏi hệ thống lưu trữ: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("Error checking existence of key '{}': {}", key, e.getMessage(), e);
            throw new DomainException(ErrorCode.STORAGE_ERROR, "Lỗi khi kiểm tra file tồn tại: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error checking existence of key '{}': {}", key, e.getMessage(), e);
            throw new DomainException(ErrorCode.STORAGE_ERROR, "Lỗi khi kiểm tra file tồn tại: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration duration) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for key '{}': {}", key, e.getMessage(), e);
            throw new DomainException(ErrorCode.STORAGE_ERROR, "Lỗi khi tạo URL tải file: " + e.getMessage(), e);
        }
    }
}

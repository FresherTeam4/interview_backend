package com.baseProject.myBaseProject.storage.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.StorageProperties;
import com.baseProject.myBaseProject.exception.StorageUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class MinioFileStorageServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;

    private MinioFileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "http://localhost:9000",
                "http://localhost:9000",
                "interview-cv",
                "us-east-1",
                "access-key",
                "secret-key",
                300);
        service = new MinioFileStorageService(s3Client, s3Presigner, properties);
    }

    @Test
    void deleteUsesConfiguredBucketAndExactKey() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        service.delete("jd/7/file.pdf");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-cv");
        assertThat(requestCaptor.getValue().key()).isEqualTo("jd/7/file.pdf");
    }

    @Test
    void deleteTranslatesSdkFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("storage unavailable"));

        assertThatThrownBy(() -> service.delete("jd/7/file.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(SdkClientException.class);
    }
}

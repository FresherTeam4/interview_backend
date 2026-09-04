package com.baseProject.myBaseProject.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baseProject.myBaseProject.config.properites.StorageProperties;
import com.baseProject.myBaseProject.exception.StorageException;
import com.baseProject.myBaseProject.storage.impl.MinioStorageService;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private StorageProperties properties;
    private MinioStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties(
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                "interview-store",
                "us-east-1"
        );
        storageService = new MinioStorageService(s3Client, s3Presigner, properties);
    }

    @Test
    void upload_shouldCallS3ClientPutObject() {
        byte[] content = "Hello PDF Content".getBytes(StandardCharsets.UTF_8);
        String key = "cvs/user1/test.pdf";

        storageService.upload(key, content, "application/pdf");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals("interview-store", capturedRequest.bucket());
        assertEquals(key, capturedRequest.key());
        assertEquals("application/pdf", capturedRequest.contentType());
        assertEquals(content.length, capturedRequest.contentLength());
    }

    @Test
    void download_shouldReturnBytesWhenObjectExists() {
        String key = "cvs/user1/test.pdf";
        byte[] expectedBytes = "File content".getBytes(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> mockResponseBytes = mock(ResponseBytes.class);
        when(mockResponseBytes.asByteArray()).thenReturn(expectedBytes);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(mockResponseBytes);

        byte[] result = storageService.download(key);

        assertNotNull(result);
        assertArrayEquals(expectedBytes, result);
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void download_shouldThrowStorageExceptionWhenObjectNotFound() {
        String key = "cvs/user1/notfound.pdf";
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Object not found").build());

        assertThrows(StorageException.class, () -> storageService.download(key));
    }

    @Test
    void delete_shouldCallS3ClientDeleteObject() {
        String key = "cvs/user1/test.pdf";

        storageService.delete(key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertEquals("interview-store", captor.getValue().bucket());
        assertEquals(key, captor.getValue().key());
    }

    @Test
    void exists_shouldReturnTrueWhenHeadObjectSucceeds() {
        String key = "cvs/user1/test.pdf";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        boolean exists = storageService.exists(key);

        assertTrue(exists);
    }

    @Test
    void exists_shouldReturnFalseWhenNoSuchKeyException() {
        String key = "cvs/user1/test.pdf";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not Found").build());

        boolean exists = storageService.exists(key);

        assertFalse(exists);
    }
}

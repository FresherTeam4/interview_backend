package com.baseProject.myBaseProject.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.JobDescriptionHasNoFileException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidTextException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotFoundException;
import com.baseProject.myBaseProject.exception.StorageUnavailableException;
import com.baseProject.myBaseProject.jd.JobDescriptionFileProcessor;
import com.baseProject.myBaseProject.jd.JobDescriptionFileProcessor.ProcessedFile;
import com.baseProject.myBaseProject.mapper.JobDescriptionMapper;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.storage.FileStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class JobDescriptionFileServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long JD_ID = 12L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final byte[] FILE_CONTENT =
            "original file bytes".getBytes(StandardCharsets.UTF_8);
    private static final String EXTRACTED_TEXT =
            "Build and maintain Java backend services for the interview platform.";

    @Mock
    private JobDescriptionRepository jobDescriptionRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private JobDescriptionFileProcessor fileProcessor;
    @Mock
    private FileStorageService fileStorage;
    @Mock
    private JobDescriptionFilePersistenceService filePersistenceService;

    private JobDescriptionServiceImpl service;
    private MockMultipartFile upload;

    @BeforeEach
    void setUp() {
        service = new JobDescriptionServiceImpl(
                jobDescriptionRepository,
                userAccountRepository,
                new JobDescriptionMapper(),
                new JobDescriptionProperties(10, 200, 3, 2_097_152, 20),
                fileProcessor,
                fileStorage,
                filePersistenceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        upload = new MockMultipartFile(
                "file", "backend.pdf", "application/pdf", FILE_CONTENT);
    }

    @Test
    void createFileValidatesUploadsThenPersistsDraft() {
        ProcessedFile processed = processedFile();
        when(fileProcessor.process(upload)).thenReturn(processed);
        when(filePersistenceService.persist(eq(USER_ID), any()))
                .thenAnswer(invocation -> persisted(invocation.getArgument(1)));

        JobDescriptionResponse response = service.createFile(
                USER_ID, "  Java Backend Engineer  ", upload);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).upload(
                keyCaptor.capture(), eq(FILE_CONTENT), eq("application/pdf"));
        assertThat(keyCaptor.getValue())
                .matches("jd/7/[0-9a-f-]{36}\\.pdf");

        ArgumentCaptor<JobDescriptionFilePersistenceService.FileDraft> draftCaptor =
                ArgumentCaptor.forClass(JobDescriptionFilePersistenceService.FileDraft.class);
        verify(filePersistenceService).persist(eq(USER_ID), draftCaptor.capture());
        JobDescriptionFilePersistenceService.FileDraft draft = draftCaptor.getValue();
        assertThat(draft.title()).isEqualTo("Java Backend Engineer");
        assertThat(draft.originalFilename()).isEqualTo("backend.pdf");
        assertThat(draft.storageKey()).isEqualTo(keyCaptor.getValue());
        assertThat(draft.fileSizeBytes()).isEqualTo(FILE_CONTENT.length);
        assertThat(draft.checksumSha256()).matches("[0-9a-f]{64}");
        assertThat(draft.text()).isEqualTo(EXTRACTED_TEXT);
        assertThat(draft.now()).isEqualTo(NOW);

        assertThat(response.sourceType()).isEqualTo(JobDescriptionSourceType.FILE);
        assertThat(response.status()).isEqualTo(JobDescriptionStatus.DRAFT);
        assertThat(response.rawText()).isEqualTo(EXTRACTED_TEXT);
        assertThat(response.confirmedText()).isEqualTo(EXTRACTED_TEXT);

        InOrder order = inOrder(fileProcessor, fileStorage, filePersistenceService);
        order.verify(fileProcessor).process(upload);
        order.verify(fileStorage).upload(any(), any(), any());
        order.verify(filePersistenceService).persist(eq(USER_ID), any());
    }

    @Test
    void createFileUsesSafeFilenameAsFallbackTitle() {
        when(fileProcessor.process(upload)).thenReturn(processedFile());
        when(filePersistenceService.persist(eq(USER_ID), any()))
                .thenAnswer(invocation -> persisted(invocation.getArgument(1)));

        JobDescriptionResponse response = service.createFile(USER_ID, "   ", upload);

        assertThat(response.title()).isEqualTo("backend.pdf");
    }

    @Test
    void createFileRejectsExtractedTextOutsideRangeBeforeUpload() {
        when(fileProcessor.process(upload)).thenReturn(new ProcessedFile(
                "backend.pdf", "pdf", "application/pdf", FILE_CONTENT, "short"));

        assertThatThrownBy(() -> service.createFile(USER_ID, null, upload))
                .isInstanceOf(JobDescriptionInvalidTextException.class);

        verifyNoInteractions(fileStorage, filePersistenceService);
    }

    @Test
    void createFileDoesNotPersistWhenUploadFails() {
        when(fileProcessor.process(upload)).thenReturn(processedFile());
        StorageUnavailableException failure = new StorageUnavailableException(
                new IllegalStateException("storage down"));
        doThrow(failure).when(fileStorage).upload(any(), any(), any());

        assertThatThrownBy(() -> service.createFile(USER_ID, null, upload))
                .isSameAs(failure);

        verifyNoInteractions(filePersistenceService);
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void createFileDeletesUploadedObjectWhenPersistenceFails() {
        when(fileProcessor.process(upload)).thenReturn(processedFile());
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("insert failed");
        when(filePersistenceService.persist(eq(USER_ID), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.createFile(USER_ID, null, upload))
                .isSameAs(failure);

        ArgumentCaptor<String> uploadedKey = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).upload(uploadedKey.capture(), eq(FILE_CONTENT), eq("application/pdf"));
        verify(fileStorage).delete(uploadedKey.getValue());
    }

    @Test
    void createFileKeepsOriginalPersistenceFailureWhenCompensationFails() {
        when(fileProcessor.process(upload)).thenReturn(processedFile());
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("insert failed");
        when(filePersistenceService.persist(eq(USER_ID), any())).thenThrow(failure);
        doThrow(new StorageUnavailableException(new IllegalStateException("delete failed")))
                .when(fileStorage).delete(any());

        assertThatThrownBy(() -> service.createFile(USER_ID, null, upload))
                .isSameAs(failure);
    }

    @Test
    void fileUrlUsesOwnershipScopedResourceAndPresignsStoredFile() {
        JobDescription jobDescription = fileJobDescription("jd/7/file.pdf");
        Instant expiresAt = Instant.parse("2026-08-26T08:05:00Z");
        when(jobDescriptionRepository.findByIdAndUserIdAndActiveTrue(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));
        when(fileStorage.presignGet("jd/7/file.pdf"))
                .thenReturn(new FileStorageService.PresignedUrl(
                        "https://storage.example/file", expiresAt));

        JobDescriptionFileUrlResponse response = service.fileUrl(USER_ID, JD_ID);

        assertThat(response.url()).isEqualTo("https://storage.example/file");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void fileUrlRejectsTextSourceWithoutCallingStorage() {
        JobDescription jobDescription = JobDescription.builder()
                .id(JD_ID)
                .sourceType(JobDescriptionSourceType.TEXT)
                .build();
        when(jobDescriptionRepository.findByIdAndUserIdAndActiveTrue(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));

        assertThatThrownBy(() -> service.fileUrl(USER_ID, JD_ID))
                .isInstanceOf(JobDescriptionHasNoFileException.class);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void fileUrlHidesMissingOrOtherUsersResource() {
        when(jobDescriptionRepository.findByIdAndUserIdAndActiveTrue(JD_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fileUrl(USER_ID, JD_ID))
                .isInstanceOf(JobDescriptionNotFoundException.class);

        verifyNoInteractions(fileStorage);
    }

    private static ProcessedFile processedFile() {
        return new ProcessedFile(
                "backend.pdf", "pdf", "application/pdf", FILE_CONTENT, EXTRACTED_TEXT);
    }

    private static JobDescription persisted(
            JobDescriptionFilePersistenceService.FileDraft draft) {
        return JobDescription.builder()
                .id(JD_ID)
                .title(draft.title())
                .sourceType(JobDescriptionSourceType.FILE)
                .status(JobDescriptionStatus.DRAFT)
                .originalFilename(draft.originalFilename())
                .storageKey(draft.storageKey())
                .contentType(draft.contentType())
                .fileSizeBytes(draft.fileSizeBytes())
                .checksumSha256(draft.checksumSha256())
                .rawText(draft.text())
                .confirmedText(draft.text())
                .active(true)
                .createdAt(draft.now())
                .updatedAt(draft.now())
                .build();
    }

    private static JobDescription fileJobDescription(String storageKey) {
        return JobDescription.builder()
                .id(JD_ID)
                .sourceType(JobDescriptionSourceType.FILE)
                .storageKey(storageKey)
                .build();
    }
}

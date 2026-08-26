package com.baseProject.myBaseProject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.mapper.CvDocumentMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.CvDocumentService;
import com.baseProject.myBaseProject.service.CvFileValidator;
import com.baseProject.myBaseProject.service.CvParsingService;
import com.baseProject.myBaseProject.service.FileStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CvDocumentServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");

    @Mock
    private CvDocumentRepository cvDocumentRepository;
    @Mock
    private CandidateProfileRepository candidateProfileRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private FileStorageService fileStorage;
    @Mock
    private CvFileValidator cvFileValidator;
    @Mock
    private CvParsingService cvParsingService;
    @Mock
    private CvDocumentMapper responseMapper;

    private CvDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CvDocumentServiceImpl(
                cvDocumentRepository,
                candidateProfileRepository,
                userAccountRepository,
                fileStorage,
                cvFileValidator,
                cvParsingService,
                new CvProperties(5_242_880L, 10, 10),
                responseMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listLoadsAllProfilesInOneBatch() {
        CvDocument first = document(1L);
        CvDocument second = document(2L);
        CandidateProfile profile = CandidateProfile.builder()
                .id(11L)
                .cvDocument(first)
                .build();
        CvDocumentResponse firstResponse = response(1L);
        CvDocumentResponse secondResponse = response(2L);

        when(cvDocumentRepository.findByUserIdAndActiveTrueOrderByUploadedAtDesc(USER_ID))
                .thenReturn(List.of(first, second));
        when(candidateProfileRepository.findByCvDocumentIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(profile));
        when(responseMapper.toResponse(first, profile)).thenReturn(firstResponse);
        when(responseMapper.toResponse(second, null)).thenReturn(secondResponse);

        List<CvDocumentResponse> result = service.list(USER_ID);

        assertEquals(List.of(firstResponse, secondResponse), result);
        verify(candidateProfileRepository).findByCvDocumentIdIn(List.of(1L, 2L));
        verify(candidateProfileRepository, never()).findByCvDocumentId(any());
    }

    @Test
    void listSkipsProfileQueryWhenUserHasNoDocuments() {
        when(cvDocumentRepository.findByUserIdAndActiveTrueOrderByUploadedAtDesc(USER_ID))
                .thenReturn(List.of());

        assertEquals(List.of(), service.list(USER_ID));

        verifyNoInteractions(candidateProfileRepository, responseMapper);
    }

    @Test
    void uploadReadsValidFileThenStoresDocumentAndSubmitsParse() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] content = {1, 2, 3};
        UserAccount user = UserAccount.builder().id(USER_ID).build();
        CvDocument saved = document(42L);
        CvDocumentResponse response = response(42L);

        when(file.getOriginalFilename()).thenReturn("cv.pdf");
        when(cvFileValidator.validateAndRead(file)).thenReturn(content);
        when(cvDocumentRepository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(0L);
        when(cvDocumentRepository
                .findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
                        any(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(userAccountRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(cvDocumentRepository.save(any(CvDocument.class))).thenReturn(saved);
        when(responseMapper.toResponse(saved, null)).thenReturn(response);

        CvDocumentService.CvUploadResult result = service.upload(USER_ID, file);

        assertEquals(response, result.document());
        assertFalse(result.reusedExisting());
        verify(fileStorage).upload(anyString(), any(byte[].class), anyString());
        verify(cvParsingService).parseAsync(42L);

        ArgumentCaptor<CvDocument> documentCaptor = ArgumentCaptor.forClass(CvDocument.class);
        verify(cvDocumentRepository).save(documentCaptor.capture());
        CvDocument newDocument = documentCaptor.getValue();
        assertEquals("cv.pdf", newDocument.getOriginalFilename());
        assertEquals(3L, newDocument.getFileSizeBytes());
        assertEquals(CvDocumentStatus.UPLOADED, newDocument.getStatus());
        assertEquals(NOW, newDocument.getUploadedAt());

        InOrder order = inOrder(cvFileValidator, cvDocumentRepository, fileStorage,
                userAccountRepository, cvParsingService);
        order.verify(cvFileValidator).validateAndRead(file);
        order.verify(cvDocumentRepository).countByUserIdAndActiveTrue(USER_ID);
        order.verify(cvDocumentRepository)
                .findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
                        any(), anyString(), any());
        order.verify(fileStorage).upload(anyString(), any(byte[].class), anyString());
        order.verify(userAccountRepository).getReferenceById(USER_ID);
        order.verify(cvDocumentRepository).save(any(CvDocument.class));
        order.verify(cvParsingService).parseAsync(42L);
    }

    private static CvDocument document(Long id) {
        return CvDocument.builder()
                .id(id)
                .active(true)
                .status(CvDocumentStatus.UPLOADED)
                .uploadedAt(NOW)
                .build();
    }

    private static CvDocumentResponse response(Long id) {
        return new CvDocumentResponse(
                id, "cv.pdf", "application/pdf", 3L, CvDocumentStatus.UPLOADED,
                null, NOW, null, null, false, null);
    }
}

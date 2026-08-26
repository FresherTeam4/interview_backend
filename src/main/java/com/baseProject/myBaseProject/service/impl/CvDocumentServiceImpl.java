package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.dto.cv.CvFileUrlResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.exception.CvLimitReachedException;
import com.baseProject.myBaseProject.exception.CvNotFoundException;
import com.baseProject.myBaseProject.exception.CvParseFailedException;
import com.baseProject.myBaseProject.exception.CvParseInProgressException;
import com.baseProject.myBaseProject.exception.CvParseNotRetryableException;
import com.baseProject.myBaseProject.mapper.CvDocumentMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.CvDocumentService;
import com.baseProject.myBaseProject.service.CvFileValidator;
import com.baseProject.myBaseProject.service.CvParsingService;
import com.baseProject.myBaseProject.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CvDocumentServiceImpl implements CvDocumentService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String STORAGE_KEY_FORMAT = "cv/%d/%s.pdf";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final int FILENAME_LENGTH = 255;
    private static final String FALLBACK_FILENAME = "cv.pdf";

    private final CvDocumentRepository cvDocumentRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserAccountRepository userAccountRepository;
    private final FileStorageService fileStorage;
    private final CvFileValidator cvFileValidator;
    private final CvParsingService cvParsingService;
    private final CvProperties cvProperties;
    private final CvDocumentMapper responseMapper;
    private final Clock clock;

    @Override
    public CvUploadResult upload(Long userId, MultipartFile file) {
        byte[] content = cvFileValidator.validateAndRead(file);
        ensureUploadCapacity(userId);

        String checksum = sha256Hex(content);
        Optional<CvDocument> reusable = findReusableDocument(userId, checksum);
        if (reusable.isPresent()) {
            return reuseDocument(reusable.get());
        }

        CvDocument document = storeNewDocument(userId, file, content, checksum);
        submitParse(document);

        return new CvUploadResult(responseMapper.toResponse(document, null), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CvDocumentResponse> list(Long userId) {
        List<CvDocument> documents =
                cvDocumentRepository.findByUserIdAndActiveTrueOrderByUploadedAtDesc(userId);
        Map<Long, CandidateProfile> profilesByDocumentId = loadProfilesByDocumentId(documents);

        return documents.stream()
                .map(document -> responseMapper.toResponse(
                        document, profilesByDocumentId.get(document.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CvDocumentResponse get(Long userId, Long cvId) {
        return loadResponse(requireActiveDocument(userId, cvId));
    }

    @Override
    @Transactional(readOnly = true)
    public CvFileUrlResponse fileUrl(Long userId, Long cvId) {
        CvDocument document = cvDocumentRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(CvNotFoundException::new);

        FileStorageService.PresignedUrl presigned = fileStorage.presignGet(document.getStorageKey());

        return new CvFileUrlResponse(presigned.url(), presigned.expiresAt());
    }

    @Override
    public CvDocumentResponse retryParse(Long userId, Long cvId) {
        CvDocument document = requireActiveDocument(userId, cvId);

        switch (document.getStatus()) {
            case FAILED -> {
                document.prepareForRetry();
                cvDocumentRepository.save(document);

                submitParse(document);
            }
            case UPLOADED, PARSING -> throw new CvParseInProgressException();
            case PARSED -> throw new CvParseNotRetryableException();
        }

        return responseMapper.toResponse(document, null);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long cvId) {
        CvDocument document = requireActiveDocument(userId, cvId);

        if (document.getStatus() == CvDocumentStatus.PARSING) {
            throw new CvParseInProgressException();
        }

        document.deactivate();
    }

    private CvDocument requireActiveDocument(Long userId, Long cvId) {
        return cvDocumentRepository.findByIdAndUserIdAndActiveTrue(cvId, userId)
                .orElseThrow(CvNotFoundException::new);
    }

    private void submitParse(CvDocument document) {
        try {
            cvParsingService.parseAsync(document.getId());
        } catch (TaskRejectedException e) {
            CvParseFailedException failure = CvParseFailedException.queueFull(e);
            log.warn("Hàng đợi bóc tách đầy, cvDocumentId={}", document.getId(), e);
            document.markFailed(failure.getStatusMessage());
            cvDocumentRepository.save(document);
        }
    }

    private void ensureUploadCapacity(Long userId) {
        if (cvDocumentRepository.countByUserIdAndActiveTrue(userId) >= cvProperties.maxPerUser()) {
            throw new CvLimitReachedException(cvProperties.maxPerUser());
        }
    }

    private Optional<CvDocument> findReusableDocument(Long userId, String checksum) {
        return cvDocumentRepository
                .findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
                        userId, checksum, CvDocumentStatus.PARSED);
    }

    private CvUploadResult reuseDocument(CvDocument document) {
        if (!document.isActive()) {
            document.reactivate();
            cvDocumentRepository.save(document);
        }
        return new CvUploadResult(loadResponse(document), true);
    }

    private CvDocument storeNewDocument(Long userId,
                                        MultipartFile file,
                                        byte[] content,
                                        String checksum) {
        String storageKey = STORAGE_KEY_FORMAT.formatted(userId, UUID.randomUUID());
        fileStorage.upload(storageKey, content, PDF_CONTENT_TYPE);

        return cvDocumentRepository.save(CvDocument.builder()
                .user(userAccountRepository.getReferenceById(userId))
                .storageKey(storageKey)
                .originalFilename(safeFilename(file.getOriginalFilename()))
                .contentType(PDF_CONTENT_TYPE)
                .fileSizeBytes((long) content.length)
                .checksumSha256(checksum)
                .status(CvDocumentStatus.UPLOADED)
                .uploadedAt(clock.instant())
                .build());
    }

    private CvDocumentResponse loadResponse(CvDocument document) {
        CandidateProfile profile = candidateProfileRepository
                .findByCvDocumentId(document.getId())
                .orElse(null);
        return responseMapper.toResponse(document, profile);
    }

    private Map<Long, CandidateProfile> loadProfilesByDocumentId(List<CvDocument> documents) {
        if (documents.isEmpty()) {
            return Map.of();
        }

        List<Long> documentIds = documents.stream().map(CvDocument::getId).toList();
        Map<Long, CandidateProfile> result = new HashMap<>();
        candidateProfileRepository.findByCvDocumentIdIn(documentIds)
                .forEach(profile -> result.put(profile.getCvDocument().getId(), profile));
        return result;
    }

    // hash file content to hex string
    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " is required but not available", e);
        }
    }

    private String safeFilename(String rawFilename) {
        if (rawFilename == null) {
            return FALLBACK_FILENAME;
        }

        String name = rawFilename.trim();
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1).trim();
        }

        if (name.isEmpty()) {
            return FALLBACK_FILENAME;
        }

        return name.length() <= FILENAME_LENGTH ? name : name.substring(0, FILENAME_LENGTH);
    }
}

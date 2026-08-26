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
import java.util.HexFormat;
import java.util.List;
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
    private final Clock clock;

    @Override
    public CvUploadResult upload(Long userId, MultipartFile file) {

        // validate
        byte[] content = cvFileValidator.validateAndRead(file);

        if (cvDocumentRepository.countByUserIdAndActiveTrue(userId) >= cvProperties.maxPerUser()) {
            throw new CvLimitReachedException(cvProperties.maxPerUser());
        }

        String checksum = sha256Hex(content);

        // check if already has in db
        Optional<CvDocument> reusable = cvDocumentRepository
                .findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
                        userId, checksum, CvDocumentStatus.PARSED);
        if (reusable.isPresent()) {
            CvDocument existing = reusable.get();
            if (!existing.isActive()) {
                existing.setActive(true);
                cvDocumentRepository.save(existing);
            }
            return new CvUploadResult(toResponse(existing), true);
        }

        //upload storage
        String storageKey = STORAGE_KEY_FORMAT.formatted(userId, UUID.randomUUID());
        fileStorage.upload(storageKey, content, PDF_CONTENT_TYPE);

        CvDocument document = cvDocumentRepository.save(CvDocument.builder()
                .user(userAccountRepository.getReferenceById(userId))
                .storageKey(storageKey)
                .originalFilename(safeFilename(file.getOriginalFilename()))
                .contentType(PDF_CONTENT_TYPE)
                .fileSizeBytes((long) content.length)
                .checksumSha256(checksum)
                .status(CvDocumentStatus.UPLOADED)
                .uploadedAt(clock.instant())
                .build());

        submitParse(document);

        return new CvUploadResult(toResponse(document, null), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CvDocumentResponse> list(Long userId) {
        return cvDocumentRepository.findByUserIdAndActiveTrueOrderByUploadedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CvDocumentResponse get(Long userId, Long cvId) {
        return toResponse(requireActiveDocument(userId, cvId));
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
                document.setStatus(CvDocumentStatus.UPLOADED);
                document.setStatusMessage(null);
                cvDocumentRepository.save(document);

                submitParse(document);
            }
            case UPLOADED, PARSING -> throw new CvParseInProgressException();
            case PARSED -> throw new CvParseNotRetryableException();
        }

        return toResponse(document, null);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long cvId) {
        CvDocument document = requireActiveDocument(userId, cvId);

        if (document.getStatus() == CvDocumentStatus.PARSING) {
            throw new CvParseInProgressException();
        }

        document.setActive(false);

        cvDocumentRepository.save(document);
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
            document.setStatus(CvDocumentStatus.FAILED);
            document.setStatusMessage(failure.getStatusMessage());
            cvDocumentRepository.save(document);
        }
    }

    private CvDocumentResponse toResponse(CvDocument document) {
        return toResponse(document, candidateProfileRepository.findByCvDocumentId(document.getId())
                .orElse(null));
    }

    private CvDocumentResponse toResponse(CvDocument document, CandidateProfile profile) {
        return new CvDocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getStatus(),
                document.getStatusMessage(),
                document.getUploadedAt(),
                document.getParsedAt(),
                profile == null ? null : profile.getId(),
                profile != null && profile.isConfirmed(),
                profile == null ? null : profile.getHeadline());
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

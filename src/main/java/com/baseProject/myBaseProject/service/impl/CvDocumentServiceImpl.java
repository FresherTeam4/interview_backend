package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.cv.CvProcessingService;
import com.baseProject.myBaseProject.cv.validation.CvFileValidator;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.mapper.CvDocumentMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.CvDocumentService;
import com.baseProject.myBaseProject.storage.StorageService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CvDocumentServiceImpl implements CvDocumentService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String STORAGE_KEY_FORMAT = "cv/%d/%s.pdf";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String FALLBACK_FILENAME = "cv.pdf";
    private static final int MAX_FILENAME_LENGTH = 255;

    private final CvDocumentRepository cvDocumentRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserAccountRepository userAccountRepository;
    private final StorageService storageService;
    private final CvFileValidator cvFileValidator;
    private final CvProcessingService cvProcessingService;
    private final CvProperties cvProperties;
    private final CvDocumentMapper cvDocumentMapper;
    private final Clock clock;

    @Override
    public CvUploadResult upload(Long userId, MultipartFile file) {
        byte[] content = cvFileValidator.validateAndRead(file);
        String checksum = sha256Hex(content);

        Optional<CvDocument> reusableDocument = cvDocumentRepository
                .findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
                        userId, checksum, CvDocumentStatus.PARSED);
        if (reusableDocument.isPresent()) {
            // Dùng lại profile cũ để giữ chỉnh sửa tay và tránh tốn thêm một lần gọi AI.
            return reuse(userId, reusableDocument.get());
        }

        // Chỉ kiểm tra hạn mức khi thật sự tạo mới hoặc kích hoạt lại một CV đã xóa.
        ensureUploadCapacity(userId);

        // upload cv vào server
        CvDocument document = store(userId, file.getOriginalFilename(), content, checksum);

        submitProcessing(document);

        return new CvUploadResult(cvDocumentMapper.toResponse(document, null), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CvDocumentResponse> list(Long userId) {
        List<CvDocument> documents =
                cvDocumentRepository.findByUserIdAndActiveTrueOrderByUploadedAtDesc(userId);
        Map<Long, CandidateProfile> profilesByDocumentId = loadProfilesByDocumentId(documents);

        return documents.stream()
                .map(document -> cvDocumentMapper.toResponse(
                        document, profilesByDocumentId.get(document.getId())))
                .toList();
    }

    private CvUploadResult reuse(Long userId, CvDocument document) {
        if (!document.isActive()) {
            ensureUploadCapacity(userId);
            document.reactivate();
            document = cvDocumentRepository.save(document);
        }

        CandidateProfile profile = candidateProfileRepository
                .findByCvDocumentId(document.getId())
                .orElse(null);
        return new CvUploadResult(cvDocumentMapper.toResponse(document, profile), true);
    }

    private void ensureUploadCapacity(Long userId) {
        long currentCount = cvDocumentRepository.countByUserIdAndActiveTrue(userId);
        if (currentCount >= cvProperties.maxPerUser()) {
            throw new DomainException(
                    ErrorCode.CV_LIMIT_REACHED,
                    "You can keep at most %d CVs".formatted(cvProperties.maxPerUser()));
        }
    }

    private CvDocument store(Long userId,
                             String originalFilename,
                             byte[] content,
                             String checksum) {
        String storageKey = STORAGE_KEY_FORMAT.formatted(userId, UUID.randomUUID());
        storageService.upload(storageKey, content, PDF_CONTENT_TYPE);

        return cvDocumentRepository.save(CvDocument.builder()
                .user(userAccountRepository.getReferenceById(userId))
                .storageKey(storageKey)
                .originalFilename(safeFilename(originalFilename))
                .contentType(PDF_CONTENT_TYPE)
                .fileSizeBytes((long) content.length)
                .checksumSha256(checksum)
                .status(CvDocumentStatus.UPLOADED)
                .uploadedAt(clock.instant())
                .build());
    }

    private void submitProcessing(CvDocument document) {
        try {
            // Đẩy sang executor để request upload không phải chờ AI xử lý CV.
            cvProcessingService.processAsync(document.getId());
        } catch (TaskRejectedException e) {
            log.warn("CV parsing queue is full, cvDocumentId={}", document.getId(), e);
            document.markFailed("CV parsing queue is temporarily full; please retry later");
            cvDocumentRepository.save(document);
        }
    }

    private Map<Long, CandidateProfile> loadProfilesByDocumentId(List<CvDocument> documents) {
        if (documents.isEmpty()) {
            return Map.of();
        }

        // Lấy toàn bộ profile bằng một query để không phát sinh N+1 khi map danh sách CV.
        List<Long> documentIds = documents.stream().map(CvDocument::getId).toList();
        Map<Long, CandidateProfile> profilesByDocumentId = new HashMap<>();
        candidateProfileRepository.findByCvDocumentIdIn(documentIds)
                .forEach(profile -> profilesByDocumentId.put(
                        profile.getCvDocument().getId(), profile));
        return profilesByDocumentId;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " is unavailable", e);
        }
    }

    private String safeFilename(String rawFilename) {
        if (rawFilename == null) {
            return FALLBACK_FILENAME;
        }

        String filename = rawFilename.trim();
        int lastSeparator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            filename = filename.substring(lastSeparator + 1).trim();
        }

        if (filename.isEmpty()) {
            return FALLBACK_FILENAME;
        }
        return filename.length() <= MAX_FILENAME_LENGTH
                ? filename
                : filename.substring(0, MAX_FILENAME_LENGTH);
    }
}

package com.baseProject.myBaseProject.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baseProject.myBaseProject.config.properites.CvStorageProperties;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.dto.cv.CvParseOutcome;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.exception.CvAlreadyParsedException;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.InvalidCvFileException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.CvParseResultRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import com.baseProject.myBaseProject.service.CvDocumentService;
import com.baseProject.myBaseProject.service.CvParserService;
import com.baseProject.myBaseProject.service.CvStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * US-1: upload CV dạng PDF, lưu file và tạo bản ghi cv_documents.
 * US-2: đọc CV bằng AI rồi dựng hồ sơ ứng viên.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvDocumentServiceImpl implements CvDocumentService {
    private static final String APPLICATION_PDF = "application/pdf";

    private final CvDocumentRepository cvDocumentRepository;
    private final CvParseResultRepository parseResultRepository;
    private final UserAccountRepository userAccountRepository;
    private final CvStorageService cvStorageService;
    private final CvParserService cvParserService;
    private final CandidateProfileService candidateProfileService;
    private final CvStorageProperties storageProperties;
    private final Clock clock;

    /* ------------------------------------------------------------------ */
    /*  US-1: Upload CV                                                    */
    /*  Lưu ý: store() ghi file trước insert DB, nên nếu DB rollback thì   */
    /*  file vẫn nằm trên đĩa (orphan). CV cũ cũng không bị xoá file khi   */
    /*  bị deactivate. Chấp nhận được ở phạm vi hiện tại.                   */
    /*  deactivateAllByUserId + insert không có unique partial index nên     */
    /*  2 request upload đồng thời có thể tạo 2 CV active. Rủi ro thấp.    */
    /* ------------------------------------------------------------------ */

    @Override
    @Transactional
    public CvDocumentResponse upload(Long userId, MultipartFile file) {
        validateFile(file);

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(Message.USER_NOT_FOUND));

        String storageKey = cvStorageService.store(userId, file);

        // Mỗi user chỉ có một CV active, tắt cờ của các CV cũ.
        cvDocumentRepository.deactivateAllByUserId(userId);

        CvDocument document = CvDocument.builder()
                .user(user)
                .storageKey(storageKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .checksumSha256(sha256(file))
                .status(CvDocumentStatus.UPLOADED)
                .active(true)
                .uploadedAt(clock.instant())
                .build();

        return CvDocumentResponse.from(cvDocumentRepository.save(document));
    }

    /* ------------------------------------------------------------------ */
    /*  Danh sách & CV active                                              */
    /* ------------------------------------------------------------------ */

    @Override
    @Transactional(readOnly = true)
    public List<CvDocumentResponse> list(Long userId) {
        return cvDocumentRepository.findByUserIdOrderByUploadedAtDesc(userId)
                .stream()
                .map(CvDocumentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CvDocumentResponse activeCv(Long userId) {
        return cvDocumentRepository.findByUserIdAndActiveTrue(userId)
                .map(CvDocumentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(Message.CV_NOT_FOUND));
    }

    /* ------------------------------------------------------------------ */
    /*  US-2: Parse CV bằng AI                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public CvDocumentResponse parse(Long userId, Long cvDocumentId) {
        CvDocument document = cvDocumentRepository.findByIdAndUserId(cvDocumentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(Message.CV_NOT_FOUND));

        // Chỉ cho parse CV đang active để tránh candidate_profiles.cv_document_id
        // trỏ vào CV không active trong khi cờ active nằm ở CV mới.
        if (!document.isActive()) {
            throw new InvalidCvFileException(Message.CV_NOT_ACTIVE);
        }

        // Mỗi CV chỉ parse một lần; muốn parse lại phải upload CV mới.
        if (parseResultRepository.findByCvDocumentId(cvDocumentId).isPresent()) {
            throw new CvAlreadyParsedException();
        }

        document.setStatus(CvDocumentStatus.PARSING);
        cvDocumentRepository.save(document);

        try {
            byte[] pdfBytes = cvStorageService.read(document.getStorageKey());
            CvParseOutcome outcome = cvParserService.parse(pdfBytes);
            candidateProfileService.applyParseResult(cvDocumentId, outcome);
        } catch (RuntimeException ex) {
            handleParseFailed(document, ex);
            throw ex;
        }

        // Reload sau khi applyParseResult đã cập nhật status → PARSED.
        return CvDocumentResponse.from(
                cvDocumentRepository.findById(cvDocumentId)
                        .orElseThrow(() -> new ResourceNotFoundException(Message.CV_NOT_FOUND)));
    }

    /* ------------------------------------------------------------------ */
    /*  Private helpers                                                    */
    /* ------------------------------------------------------------------ */

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCvFileException(Message.CV_FILE_REQUIRED);
        }
        if (!APPLICATION_PDF.equals(file.getContentType())) {
            throw new InvalidCvFileException(Message.CV_MUST_BE_PDF);
        }
        long maxBytes = storageProperties.maxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            long maxMb = maxBytes / (1024 * 1024);
            throw new InvalidCvFileException(Message.CV_FILE_TOO_LARGE.formatted(maxMb));
        }
    }

    /** Tính SHA-256 checksum để phát hiện file trùng. */
    private static String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | java.io.IOException ex) {
            log.warn("Không tính được checksum SHA-256, bỏ qua", ex);
            return null;
        }
    }

    /**
     * Khi AI parse thất bại: ghi trạng thái FAILED + message thân thiện vào CV document
     * để FE hiển thị, đồng thời log chi tiết kỹ thuật để debug.
     * Chỉ lưu message của {@link DomainException} (do chính ta định nghĩa); các lỗi kỹ thuật
     * (SQL, HTTP body của provider) dùng message chung để không lộ thông tin nội bộ ra FE.
     */
    private void handleParseFailed(CvDocument document, RuntimeException ex) {
        log.error("Parse CV {} thất bại: {}", document.getId(), ex.getMessage(), ex);
        try {
            String userFacingMessage = (ex instanceof DomainException)
                    ? ex.getMessage()
                    : Message.CV_PARSE_FAILED;
            document.setStatus(CvDocumentStatus.FAILED);
            document.setStatusMessage(userFacingMessage);
            cvDocumentRepository.save(document);
        } catch (RuntimeException saveEx) {
            log.error("Không ghi được trạng thái FAILED cho CV {}", document.getId(), saveEx);
        }
    }
}

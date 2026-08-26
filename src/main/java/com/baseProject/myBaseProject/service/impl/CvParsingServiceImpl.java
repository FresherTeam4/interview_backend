package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.CvParseResult;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.exception.CvParseFailedException;
import com.baseProject.myBaseProject.exception.StorageUnavailableException;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.CvParseResultRepository;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import com.baseProject.myBaseProject.service.CvParserClient;
import com.baseProject.myBaseProject.service.CvParsingService;
import com.baseProject.myBaseProject.service.FileStorageService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
public class CvParsingServiceImpl implements CvParsingService {

    private final CvDocumentRepository cvDocumentRepository;
    private final CvParseResultRepository cvParseResultRepository;
    private final CandidateProfileService candidateProfileService;
    private final FileStorageService fileStorage;
    private final CvParserClient cvParserClient;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    private final Instant startedAt;

    public CvParsingServiceImpl(CvDocumentRepository cvDocumentRepository,
                                CvParseResultRepository cvParseResultRepository,
                                CandidateProfileService candidateProfileService,
                                FileStorageService fileStorage,
                                CvParserClient cvParserClient,
                                Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.cvDocumentRepository = cvDocumentRepository;
        this.cvParseResultRepository = cvParseResultRepository;
        this.candidateProfileService = candidateProfileService;
        this.fileStorage = fileStorage;
        this.cvParserClient = cvParserClient;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.startedAt = clock.instant();
    }

    @Override
    @Async(AsyncConfig.CV_PARSE_EXECUTOR)
    public void parseAsync(Long cvDocumentId) {
        try {
            runParse(cvDocumentId);
        } catch (CvParseFailedException e) {
            log.warn("Bóc tách CV thất bại, cvDocumentId={}: {}", cvDocumentId, e.getMessage(), e);
            markFailed(cvDocumentId, e.getStatusMessage());
        } catch (RuntimeException e) {
            log.error("Lỗi ngoài dự kiến khi bóc tách CV, cvDocumentId={}", cvDocumentId, e);
            markFailed(cvDocumentId, Message.PARSE_FAILED_UNEXPECTED);
        }
    }

    private void runParse(Long cvDocumentId) {
        Optional<CvDocument> found = cvDocumentRepository.findById(cvDocumentId);
        if (found.isEmpty()) {
            log.warn("Bỏ qua bóc tách: không còn cv_documents id={}", cvDocumentId);
            return;
        }

        CvDocument document = found.get();

        if (!document.isActive()) {
            log.info("Bỏ qua bóc tách: CV đã bị xóa, cvDocumentId={}", cvDocumentId);
            return;
        }

        // check if parse result exist
        if (cvParseResultRepository.existsByCvDocumentId(cvDocumentId)) {
            log.warn("Bỏ qua bóc tách: cv_parse_results đã tồn tại, cvDocumentId={}", cvDocumentId);
            markParsedIfNeeded(document);
            return;
        }

        document.setStatus(CvDocumentStatus.PARSING);
        cvDocumentRepository.save(document);

        byte[] content = downloadOrFail(document);
        CvParserClient.ParseOutcome outcome = cvParserClient.parse(content);

        Instant now = clock.instant();
        writeResultAndProfile(cvDocumentId, outcome, now);

        document.setStatus(CvDocumentStatus.PARSED);
        document.setParsedAt(now);
        document.setStatusMessage(null);
        cvDocumentRepository.save(document);
    }

    // download file from storage
    private byte[] downloadOrFail(CvDocument document) {
        try {
            return fileStorage.download(document.getStorageKey());
        } catch (StorageUnavailableException e) {
            throw CvParseFailedException.fileUnreadable(e);
        }
    }

    private void writeResultAndProfile(Long cvDocumentId,
                                       CvParserClient.ParseOutcome outcome,
                                       Instant now) {
        transactionTemplate.executeWithoutResult(status -> {
            CvDocument managed = cvDocumentRepository.findById(cvDocumentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "cv_documents id=" + cvDocumentId + " biến mất giữa lần bóc tách"));

            cvParseResultRepository.save(CvParseResult.builder()
                    .cvDocument(managed)
                    .rawJson(outcome.rawJson())
                    .schemaVersion(outcome.schemaVersion())
                    .modelName(outcome.modelName())
                    .durationMs(outcome.durationMs())
                    .tokenCost(outcome.tokenCost())
                    .createdAt(now)
                    .build());

            candidateProfileService.createFromParse(managed, outcome.payload(), now);
        });
    }

    private void markParsedIfNeeded(CvDocument document) {
        if (document.getStatus() == CvDocumentStatus.PARSED) {
            return;
        }

        document.setStatus(CvDocumentStatus.PARSED);
        document.setStatusMessage(null);
        if (document.getParsedAt() == null) {
            document.setParsedAt(clock.instant());
        }
        cvDocumentRepository.save(document);
    }

    private void markFailed(Long cvDocumentId, String statusMessage) {
        try {
            cvDocumentRepository.findById(cvDocumentId).ifPresent(document -> {
                if (document.getStatus() == CvDocumentStatus.PARSED) {
                    log.error("CV đã PARSED nhưng lần bóc tách báo lỗi, giữ nguyên trạng thái, "
                            + "cvDocumentId={}", cvDocumentId);
                    return;
                }

                document.setStatus(CvDocumentStatus.FAILED);
                document.setStatusMessage(statusMessage);
                cvDocumentRepository.save(document);
            });
        } catch (RuntimeException e) {
            log.error("Không ghi được trạng thái FAILED cho cvDocumentId={}", cvDocumentId, e);
        }
    }

    // clear parsing and uploaded cv document when server restart
    @Override
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failParsesInterruptedByRestart() {
        List<CvDocument> stranded = Stream.concat(
                        cvDocumentRepository.findByStatus(CvDocumentStatus.PARSING).stream(),
                        cvDocumentRepository.findByStatus(CvDocumentStatus.UPLOADED).stream())
                .filter(document -> document.getUploadedAt().isBefore(startedAt))
                .toList();

        if (stranded.isEmpty()) {
            return;
        }

        stranded.forEach(document -> {
            document.setStatus(CvDocumentStatus.FAILED);
            document.setStatusMessage(Message.PARSE_FAILED_INTERRUPTED_BY_RESTART);
        });
        cvDocumentRepository.saveAll(stranded);

        log.warn("Đã đánh dấu FAILED cho {} CV bị dừng giữa lúc khởi động lại", stranded.size());
    }
}

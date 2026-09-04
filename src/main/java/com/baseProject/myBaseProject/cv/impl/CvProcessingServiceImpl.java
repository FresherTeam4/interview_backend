package com.baseProject.myBaseProject.cv.impl;

import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.cv.CvParsingService;
import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.CvParseResult;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.CvParseResultRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.cv.CvProcessingService;
import com.baseProject.myBaseProject.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
public class CvProcessingServiceImpl implements CvProcessingService {

    private static final String SCHEMA_VERSION = "v1";

    private final CvDocumentRepository cvDocumentRepository;
    private final CvParseResultRepository cvParseResultRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ProfileEducationRepository educationRepository;
    private final ProfileSkillRepository skillRepository;
    private final ProfileProjectRepository projectRepository;
    private final StorageService storageService;
    private final CvParsingService cvParsingService;
    private final ProfileMapper profileMapper;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CvProcessingServiceImpl(CvDocumentRepository cvDocumentRepository,
                                   CvParseResultRepository cvParseResultRepository,
                                   CandidateProfileRepository candidateProfileRepository,
                                   ProfileEducationRepository educationRepository,
                                   ProfileSkillRepository skillRepository,
                                   ProfileProjectRepository projectRepository,
                                   StorageService storageService,
                                   CvParsingService cvParsingService,
                                   ProfileMapper profileMapper,
                                   ChatModel chatModel,
                                   ObjectMapper objectMapper,
                                   Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.cvDocumentRepository = cvDocumentRepository;
        this.cvParseResultRepository = cvParseResultRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.educationRepository = educationRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
        this.storageService = storageService;
        this.cvParsingService = cvParsingService;
        this.profileMapper = profileMapper;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Async(AsyncConfig.CV_PARSE_EXECUTOR)
    public void processAsync(Long cvDocumentId) {
        try {
            WorkItem workItem = claim(cvDocumentId);
            if (workItem == null) {
                return;
            }

            byte[] content = storageService.download(workItem.storageKey());
            long startedAt = System.currentTimeMillis();
            CvExtractionResult extraction = cvParsingService.parseCvFromPdf(content);
            int durationMs = toInteger(System.currentTimeMillis() - startedAt);

            persistResult(cvDocumentId, extraction, durationMs);
        } catch (RuntimeException e) {
            log.error("Background CV processing failed, cvDocumentId={}", cvDocumentId, e);
            markFailed(cvDocumentId, Message.CV_PARSE_FAILED);
        }
    }

    private WorkItem claim(Long cvDocumentId) {
        // Transaction ngắn chỉ nhận job; không giữ transaction trong lúc tải file và gọi AI.
        return transactionTemplate.execute(status -> {
            CvDocument document = cvDocumentRepository.findById(cvDocumentId).orElse(null);
            if (document == null || !document.isActive()
                    || document.getStatus() != CvDocumentStatus.UPLOADED) {
                return null;
            }

            if (cvParseResultRepository.existsByCvDocumentId(cvDocumentId)) {
                document.markParsed(clock.instant());
                return null;
            }

            document.markParsing();
            return new WorkItem(document.getStorageKey());
        });
    }

    private void persistResult(Long cvDocumentId,
                               CvExtractionResult extraction,
                               int measuredDurationMs) {
        Instant now = clock.instant();
        // Bản AI gốc và profile có thể sửa phải được cho vào transaction
        transactionTemplate.executeWithoutResult(status -> {
            CvDocument document = cvDocumentRepository.findById(cvDocumentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "CV document disappeared while it was being parsed"));

            if (cvParseResultRepository.existsByCvDocumentId(cvDocumentId)) {
                document.markParsed(now);
                return;
            }

            CvParseResult parseResult = CvParseResult.builder()
                    .cvDocument(document)
                    .rawJson(toJson(extraction))
                    .schemaVersion(SCHEMA_VERSION)
                    .modelName(modelName(extraction))
                    .durationMs(extraction.durationMs() == null
                            ? measuredDurationMs
                            : toInteger(extraction.durationMs()))
                    .tokenCost(extraction.totalTokens() == null
                            ? null
                            : toInteger(extraction.totalTokens()))
                    .createdAt(now)
                    .build();
            cvParseResultRepository.save(parseResult);

            CandidateProfile profile = candidateProfileRepository.save(
                    profileMapper.newProfile(document, extraction, now));
            educationRepository.saveAll(profileMapper.newEducations(profile, extraction));
            skillRepository.saveAll(profileMapper.newSkills(profile, extraction));
            projectRepository.saveAll(profileMapper.newProjects(profile, extraction));

            document.markParsed(now);
        });
    }

    private void markFailed(Long cvDocumentId, String statusMessage) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    cvDocumentRepository.findById(cvDocumentId).ifPresent(document -> {
                        // Không để lỗi đến muộn ghi đè một kết quả PARSED đã commit thành công.
                        if (document.getStatus() != CvDocumentStatus.PARSED) {
                            document.markFailed(statusMessage);
                        }
                    }));
        } catch (RuntimeException e) {
            log.error("Cannot persist FAILED status for cvDocumentId={}", cvDocumentId, e);
        }
    }

    private String toJson(CvExtractionResult extraction) {
        try {
            return objectMapper.writeValueAsString(extraction);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize parsed CV result", e);
        }
    }

    private String modelName(CvExtractionResult extraction) {
        String modelName = extraction.modelName();
        return modelName == null || modelName.isBlank()
                ? chatModel.getOptions().getModel()
                : modelName.trim();
    }

    private int toInteger(long value) {
        return (int) Math.min(Math.max(value, 0L), Integer.MAX_VALUE);
    }

    private record WorkItem(String storageKey) {
    }
}

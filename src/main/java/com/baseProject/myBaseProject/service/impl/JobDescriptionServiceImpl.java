package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.jd.CreateTextJobDescriptionRequest;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionSummaryResponse;
import com.baseProject.myBaseProject.dto.jd.UpdateJobDescriptionRequest;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.exception.JobDescriptionAlreadyConfirmedException;
import com.baseProject.myBaseProject.exception.JobDescriptionContentRequiredException;
import com.baseProject.myBaseProject.exception.JobDescriptionHasNoFileException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidTextException;
import com.baseProject.myBaseProject.exception.JobDescriptionLimitReachedException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotFoundException;
import com.baseProject.myBaseProject.jd.JobDescriptionFileProcessor;
import com.baseProject.myBaseProject.jd.JobDescriptionFileProcessor.ProcessedFile;
import com.baseProject.myBaseProject.jd.JobDescriptionFingerprint;
import com.baseProject.myBaseProject.mapper.JobDescriptionMapper;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.JobDescriptionService;
import com.baseProject.myBaseProject.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobDescriptionServiceImpl implements JobDescriptionService {

    private static final String STORAGE_KEY_FORMAT = "jd/%d/%s.%s";
    private static final int TITLE_MAX_LENGTH = 200;

    private final JobDescriptionRepository jobDescriptionRepository;
    private final UserAccountRepository userAccountRepository;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobDescriptionProperties properties;
    private final JobDescriptionFileProcessor fileProcessor;
    private final FileStorageService fileStorage;
    private final JobDescriptionFilePersistenceService filePersistenceService;
    private final JobDescriptionFingerprint jobDescriptionFingerprint;
    private final Clock clock;

    @Override
    @Transactional
    public JobDescriptionResponse createText(
            Long userId,
            CreateTextJobDescriptionRequest request) {
        String text = normalizeAndValidateText(request.text());
        UserAccount user = lockUser(userId);
        ensureCapacity(userId);

        Instant now = clock.instant();
        JobDescription jobDescription = jobDescriptionRepository.save(JobDescription.createText(
                user,
                request.title().strip(),
                jobDescriptionFingerprint.create(text),
                text,
                now));

        return jobDescriptionMapper.toResponse(jobDescription);
    }

    @Override
    public JobDescriptionResponse createFile(Long userId, String title, MultipartFile file) {
        ProcessedFile processedFile = fileProcessor.process(file);
        String extractedText = normalizeAndValidateExtractedText(processedFile.extractedText());
        String normalizedTitle = normalizeFileTitle(title, processedFile.originalFilename());
        String checksum = jobDescriptionFingerprint.create(extractedText);
        String storageKey = STORAGE_KEY_FORMAT.formatted(
                userId, UUID.randomUUID(), processedFile.extension());

        fileStorage.upload(storageKey, processedFile.content(), processedFile.contentType());

        JobDescription jobDescription;
        try {
            jobDescription = filePersistenceService.persist(
                    userId,
                    new JobDescriptionFilePersistenceService.FileDraft(
                            normalizedTitle,
                            processedFile.originalFilename(),
                            storageKey,
                            processedFile.contentType(),
                            processedFile.content().length,
                            checksum,
                            extractedText,
                            clock.instant()));
        } catch (RuntimeException persistenceFailure) {
            compensateUploadedFile(userId, storageKey);
            throw persistenceFailure;
        }

        return jobDescriptionMapper.toResponse(jobDescription);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JobDescriptionSummaryResponse> list(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<JobDescriptionSummaryResponse> result = jobDescriptionRepository
                .findByUserIdAndActiveTrue(userId, pageRequest)
                .map(jobDescriptionMapper::toSummary);
        return PageResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public JobDescriptionResponse get(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = jobDescriptionRepository
                .findByIdAndUserIdAndActiveTrue(jobDescriptionId, userId)
                .orElseThrow(JobDescriptionNotFoundException::new);
        return jobDescriptionMapper.toResponse(jobDescription);
    }

    @Override
    public JobDescriptionFileUrlResponse fileUrl(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = jobDescriptionRepository
                .findByIdAndUserIdAndActiveTrue(jobDescriptionId, userId)
                .orElseThrow(JobDescriptionNotFoundException::new);
        if (jobDescription.getSourceType() != JobDescriptionSourceType.FILE
                || jobDescription.getStorageKey() == null) {
            throw new JobDescriptionHasNoFileException();
        }

        FileStorageService.PresignedUrl presigned =
                fileStorage.presignGet(jobDescription.getStorageKey());
        return new JobDescriptionFileUrlResponse(presigned.url(), presigned.expiresAt());
    }

    @Override
    @Transactional
    public JobDescriptionResponse update(
            Long userId,
            Long jobDescriptionId,
            UpdateJobDescriptionRequest request) {
        String confirmedText = normalizeAndValidateText(request.confirmedText());
        JobDescription jobDescription = requireActiveForUpdate(userId, jobDescriptionId);
        if (!jobDescription.isDraft()) {
            throw new JobDescriptionAlreadyConfirmedException();
        }

        jobDescription.updateDraft(request.title().strip(), confirmedText, clock.instant());
        return jobDescriptionMapper.toResponse(jobDescription);
    }

    @Override
    @Transactional
    public JobDescriptionResponse confirm(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = requireActiveForUpdate(userId, jobDescriptionId);
        jobDescription.confirm(clock.instant());
        return jobDescriptionMapper.toResponse(jobDescription);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = requireActiveForUpdate(userId, jobDescriptionId);
        jobDescription.deactivate(clock.instant());
    }

    private UserAccount lockUser(Long userId) {
        return userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user no longer exists, userId=" + userId));
    }

    private void ensureCapacity(Long userId) {
        if (jobDescriptionRepository.countByUserIdAndActiveTrue(userId) >= properties.maxPerUser()) {
            throw new JobDescriptionLimitReachedException(properties.maxPerUser());
        }
    }

    private JobDescription requireActiveForUpdate(Long userId, Long jobDescriptionId) {
        return jobDescriptionRepository.findActiveOwnedByIdForUpdate(jobDescriptionId, userId)
                .orElseThrow(JobDescriptionNotFoundException::new);
    }

    private String normalizeAndValidateText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new JobDescriptionContentRequiredException();
        }

        String normalized = rawText.strip();
        if (normalized.length() < properties.minTextChars()
                || normalized.length() > properties.maxTextChars()) {
            throw new JobDescriptionInvalidTextException(
                    properties.minTextChars(),
                    properties.maxTextChars());
        }
        return normalized;
    }

    private String normalizeAndValidateExtractedText(String extractedText) {
        if (extractedText == null) {
            throw new JobDescriptionInvalidTextException(
                    properties.minTextChars(), properties.maxTextChars());
        }
        String normalized = extractedText.strip();
        if (normalized.length() < properties.minTextChars()
                || normalized.length() > properties.maxTextChars()) {
            throw new JobDescriptionInvalidTextException(
                    properties.minTextChars(),
                    properties.maxTextChars());
        }
        return normalized;
    }

    private String normalizeFileTitle(String requestedTitle, String filename) {
        String title = requestedTitle == null || requestedTitle.isBlank()
                ? filename
                : requestedTitle.strip();
        return title.length() <= TITLE_MAX_LENGTH
                ? title
                : title.substring(0, TITLE_MAX_LENGTH);
    }

    private void compensateUploadedFile(Long userId, String storageKey) {
        try {
            fileStorage.delete(storageKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Không xóa bù được file JD sau lỗi persistence, userId={}, cause={}",
                    userId,
                    cleanupFailure.getClass().getSimpleName());
        }
    }

}

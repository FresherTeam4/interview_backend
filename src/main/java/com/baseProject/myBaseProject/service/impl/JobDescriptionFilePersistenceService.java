package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.JobDescriptionLimitReachedException;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JobDescriptionFilePersistenceService {

    private final JobDescriptionRepository jobDescriptionRepository;
    private final UserAccountRepository userAccountRepository;
    private final JobDescriptionProperties properties;

    @Transactional
    public JobDescription persist(Long userId, FileDraft draft) {
        UserAccount user = userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user no longer exists, userId=" + userId));
        if (jobDescriptionRepository.countByUserIdAndActiveTrue(userId)
                >= properties.maxPerUser()) {
            throw new JobDescriptionLimitReachedException(properties.maxPerUser());
        }

        return jobDescriptionRepository.save(JobDescription.builder()
                .user(user)
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
                .build());
    }

    public record FileDraft(
            String title,
            String originalFilename,
            String storageKey,
            String contentType,
            long fileSizeBytes,
            String checksumSha256,
            String text,
            Instant now) {
    }
}

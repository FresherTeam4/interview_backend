package com.baseProject.myBaseProject.dto.jobdescription;

import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.JobDescriptionFailureStage;

import java.time.Instant;

public record JobDescriptionResponse(
        Long id,
        JobDescriptionSourceType sourceType,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        JobDescriptionStatus status,
        String errorCode,
        JobDescriptionFailureStage failureStage,
        String statusMessage,
        Instant uploadedAt,
        Instant processedAt,
        Long templateId,
        boolean templateConfirmed,
        String templateTitle) {
}

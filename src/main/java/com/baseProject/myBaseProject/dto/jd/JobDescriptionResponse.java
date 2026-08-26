package com.baseProject.myBaseProject.dto.jd;

import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;

import java.time.Instant;

public record JobDescriptionResponse(
        Long id,
        String title,
        JobDescriptionSourceType sourceType,
        JobDescriptionStatus status,
        String originalFilename,
        String rawText,
        String confirmedText,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

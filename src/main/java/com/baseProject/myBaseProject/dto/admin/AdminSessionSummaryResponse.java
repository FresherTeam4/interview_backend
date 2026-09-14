package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.time.Instant;

public record AdminSessionSummaryResponse(
        Long id,
        AdminUserReferenceResponse user,
        InterviewSessionStatus status,
        String templateTitle,
        String profileName,
        InterviewSessionMode mode,
        String languageCode,
        int durationMinutes,
        String preparationErrorCode,
        String scoringErrorCode,
        Instant createdAt,
        Instant updatedAt) {
}

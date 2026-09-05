package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

import java.time.Instant;
import java.util.List;

public record InterviewSessionResponse(
        Long id,
        long version,
        Long templateId,
        String templateTitle,
        Long profileId,
        String profileName,
        InterviewSessionStatus status,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        String jobContextSummary,
        String candidateContextSummary,
        String openingMessage,
        String preparationErrorCode,
        String preparationErrorMessage,
        Instant preparationStartedAt,
        Instant preparedAt,
        Instant createdAt,
        Instant updatedAt,
        List<InterviewFocusAreaResponse> focusAreas) {
}

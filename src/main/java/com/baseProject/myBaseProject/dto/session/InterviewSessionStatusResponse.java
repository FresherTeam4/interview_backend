package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

import java.time.Instant;

public record InterviewSessionStatusResponse(
        Long id,
        InterviewSessionStatus status,
        String templateTitle,
        String profileName,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        String preparationErrorCode,
        String preparationErrorMessage,
        Instant preparedAt) {
}

package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
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
        InterviewSessionMode mode,
        String realtimeProvider,
        String realtimeVoiceName,
        String preparationErrorCode,
        String preparationErrorMessage,
        String scoringErrorCode,
        String scoringErrorMessage,
        Instant preparedAt,
        InterviewEndReason endReason,
        Instant endedAt,
        Instant completedAt) {
}

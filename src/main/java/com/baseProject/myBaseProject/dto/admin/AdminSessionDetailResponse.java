package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

import java.time.Instant;
import java.util.List;

public record AdminSessionDetailResponse(
        Long id,
        AdminUserReferenceResponse user,
        InterviewSessionStatus status,
        String templateTitle,
        String profileName,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        InterviewSessionMode mode,
        String realtimeProvider,
        String realtimeVoiceName,
        int currentTurnIndex,
        String preparationErrorCode,
        String preparationErrorMessage,
        String planSchemaVersion,
        String planModelName,
        String planPromptVersion,
        String scoringErrorCode,
        String scoringErrorMessage,
        Instant preparationStartedAt,
        Instant preparedAt,
        Instant startedAt,
        Instant deadlineAt,
        Instant lastActivityAt,
        InterviewEndReason endReason,
        Instant endedAt,
        Instant scoringStartedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<AdminSessionTransitionResponse> transitions) {
}

package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;

/** Gom toàn bộ dữ liệu cần thiết để áp dụng một lần chuyển trạng thái session. */
public record SessionStateChange(
        SessionStatus targetStatus,
        AwaitingAction awaitingAction,
        SessionEndReason endReason,
        SessionFailureStage failureStage,
        String statusMessage,
        SessionProcessingStage processingStage,
        boolean resetProcessingAttempts,
        boolean updateLastActivity) {
}

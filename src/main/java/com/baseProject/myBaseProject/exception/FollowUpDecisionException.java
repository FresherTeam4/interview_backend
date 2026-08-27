package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

/** Failure boundary for adaptive next-turn provider calls and contract validation. */
public class FollowUpDecisionException extends RuntimeException {

    public enum Reason {
        MISSING_CREDENTIAL,
        TIMEOUT,
        PROVIDER_UNAVAILABLE,
        MALFORMED_OUTPUT,
        INVALID_OUTPUT,
        UNEXPECTED
    }

    private final Reason reason;
    private final String statusMessage;
    private final boolean retryable;

    private FollowUpDecisionException(
            Reason reason,
            String statusMessage,
            boolean retryable,
            String technicalDetail,
            Throwable cause) {
        super(technicalDetail, cause);
        this.reason = reason;
        this.statusMessage = statusMessage;
        this.retryable = retryable;
    }

    public Reason getReason() {
        return reason;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static FollowUpDecisionException noApiKey() {
        return new FollowUpDecisionException(
                Reason.MISSING_CREDENTIAL,
                Message.FOLLOW_UP_FAILED_NO_API_KEY,
                false,
                "Thiếu cấu hình app.ai.api-key",
                null);
    }

    public static FollowUpDecisionException timeout(Throwable cause) {
        return new FollowUpDecisionException(
                Reason.TIMEOUT,
                Message.FOLLOW_UP_FAILED_TIMEOUT,
                true,
                "Provider quyết định follow-up không phản hồi trong thời gian chờ",
                cause);
    }

    public static FollowUpDecisionException providerUnavailable(
            String technicalDetail,
            Throwable cause) {
        return new FollowUpDecisionException(
                Reason.PROVIDER_UNAVAILABLE,
                Message.FOLLOW_UP_FAILED_AI_UNAVAILABLE,
                true,
                "Provider quyết định follow-up không khả dụng: " + technicalDetail,
                cause);
    }

    public static FollowUpDecisionException malformedOutput(
            String technicalDetail,
            Throwable cause) {
        return new FollowUpDecisionException(
                Reason.MALFORMED_OUTPUT,
                Message.FOLLOW_UP_FAILED_INVALID_OUTPUT,
                true,
                "Phản hồi follow-up sai schema: " + technicalDetail,
                cause);
    }

    public static FollowUpDecisionException invalidOutput(String technicalDetail) {
        return new FollowUpDecisionException(
                Reason.INVALID_OUTPUT,
                Message.FOLLOW_UP_FAILED_INVALID_OUTPUT,
                false,
                "Quyết định follow-up không hợp lệ: " + technicalDetail,
                null);
    }

    public static FollowUpDecisionException unexpected(Throwable cause) {
        return new FollowUpDecisionException(
                Reason.UNEXPECTED,
                Message.FOLLOW_UP_FAILED_UNEXPECTED,
                false,
                "Lỗi ngoài dự kiến khi quyết định follow-up",
                cause);
    }
}

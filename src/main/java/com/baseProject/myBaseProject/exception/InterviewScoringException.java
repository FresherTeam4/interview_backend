package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

/** Failure boundary for scoring provider calls and output validation. */
public class InterviewScoringException extends RuntimeException {

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

    private InterviewScoringException(
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

    public static InterviewScoringException noApiKey() {
        return new InterviewScoringException(
                Reason.MISSING_CREDENTIAL,
                Message.SCORING_FAILED_NO_API_KEY,
                false,
                "Thiếu cấu hình app.ai.api-key",
                null);
    }

    public static InterviewScoringException timeout(Throwable cause) {
        return new InterviewScoringException(
                Reason.TIMEOUT,
                Message.SCORING_FAILED_TIMEOUT,
                true,
                "Provider scoring không phản hồi trong thời gian chờ",
                cause);
    }

    public static InterviewScoringException providerUnavailable(
            String technicalDetail,
            Throwable cause) {
        return new InterviewScoringException(
                Reason.PROVIDER_UNAVAILABLE,
                Message.SCORING_FAILED_AI_UNAVAILABLE,
                true,
                "Provider scoring không khả dụng: " + technicalDetail,
                cause);
    }

    public static InterviewScoringException malformedOutput(
            String technicalDetail,
            Throwable cause) {
        return new InterviewScoringException(
                Reason.MALFORMED_OUTPUT,
                Message.SCORING_FAILED_INVALID_OUTPUT,
                true,
                "Phản hồi scoring sai schema: " + technicalDetail,
                cause);
    }

    public static InterviewScoringException invalidOutput(String technicalDetail) {
        return new InterviewScoringException(
                Reason.INVALID_OUTPUT,
                Message.SCORING_FAILED_INVALID_OUTPUT,
                true,
                "Kết quả scoring không hợp lệ: " + technicalDetail,
                null);
    }

    public static InterviewScoringException unexpected(Throwable cause) {
        return new InterviewScoringException(
                Reason.UNEXPECTED,
                Message.SCORING_FAILED_UNEXPECTED,
                false,
                "Lỗi ngoài dự kiến khi scoring",
                cause);
    }
}

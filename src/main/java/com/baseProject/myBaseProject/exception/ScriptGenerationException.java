package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

/**
 * Failure boundary of base-question generation. Technical detail is only for logs; callers store
 * {@link #getStatusMessage()} on the session when M06 turns the failure into workflow state.
 */
public class ScriptGenerationException extends RuntimeException {

    public enum Reason {
        MISSING_CREDENTIAL,
        TIMEOUT,
        PROVIDER_UNAVAILABLE,
        MALFORMED_OUTPUT,
        INVALID_OUTPUT,
        DIVERSITY_REJECTED,
        UNEXPECTED
    }

    private final Reason reason;
    private final String statusMessage;
    private final boolean retryable;

    private ScriptGenerationException(
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

    public boolean isDiversityRejected() {
        return reason == Reason.DIVERSITY_REJECTED;
    }

    public static ScriptGenerationException noApiKey() {
        return new ScriptGenerationException(
                Reason.MISSING_CREDENTIAL,
                Message.SCRIPT_GENERATION_FAILED_NO_API_KEY,
                false,
                "Thiếu cấu hình app.ai.api-key",
                null);
    }

    public static ScriptGenerationException timeout(Throwable cause) {
        return new ScriptGenerationException(
                Reason.TIMEOUT,
                Message.SCRIPT_GENERATION_FAILED_TIMEOUT,
                true,
                "Provider sinh câu hỏi không phản hồi trong thời gian chờ",
                cause);
    }

    public static ScriptGenerationException providerUnavailable(
            String technicalDetail,
            Throwable cause) {
        return new ScriptGenerationException(
                Reason.PROVIDER_UNAVAILABLE,
                Message.SCRIPT_GENERATION_FAILED_AI_UNAVAILABLE,
                true,
                "Provider sinh câu hỏi không khả dụng: " + technicalDetail,
                cause);
    }

    public static ScriptGenerationException malformedOutput(
            String technicalDetail,
            Throwable cause) {
        return new ScriptGenerationException(
                Reason.MALFORMED_OUTPUT,
                Message.SCRIPT_GENERATION_FAILED_INVALID_OUTPUT,
                true,
                "Phản hồi sinh câu hỏi sai schema: " + technicalDetail,
                cause);
    }

    public static ScriptGenerationException invalidOutput(String technicalDetail) {
        return new ScriptGenerationException(
                Reason.INVALID_OUTPUT,
                Message.SCRIPT_GENERATION_FAILED_INVALID_OUTPUT,
                true,
                "Bộ câu hỏi không hợp lệ: " + technicalDetail,
                null);
    }

    public static ScriptGenerationException diversityRejected(String technicalDetail) {
        return new ScriptGenerationException(
                Reason.DIVERSITY_REJECTED,
                Message.SCRIPT_GENERATION_FAILED_INVALID_OUTPUT,
                true,
                "Bộ câu hỏi không đạt diversity: " + technicalDetail,
                null);
    }

    public static ScriptGenerationException unexpected(Throwable cause) {
        return new ScriptGenerationException(
                Reason.UNEXPECTED,
                Message.SCRIPT_GENERATION_FAILED_UNEXPECTED,
                false,
                "Lỗi ngoài dự kiến khi sinh câu hỏi",
                cause);
    }
}

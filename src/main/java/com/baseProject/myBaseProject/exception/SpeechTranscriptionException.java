package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

/** Internal failure boundary for asynchronous speech-to-text work. */
public class SpeechTranscriptionException extends RuntimeException {

    public enum Reason {
        MISSING_CREDENTIAL,
        TIMEOUT,
        PROVIDER_UNAVAILABLE,
        MALFORMED_OUTPUT,
        AUDIO_UNAVAILABLE,
        UNEXPECTED
    }

    private final Reason reason;
    private final String statusMessage;
    private final boolean retryable;

    private SpeechTranscriptionException(
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

    public static SpeechTranscriptionException noApiKey() {
        return failure(Reason.MISSING_CREDENTIAL, Message.STT_FAILED_NO_API_KEY, false, null);
    }

    public static SpeechTranscriptionException timeout(Throwable cause) {
        return failure(Reason.TIMEOUT, Message.STT_FAILED_TIMEOUT, true, cause);
    }

    public static SpeechTranscriptionException providerUnavailable(Throwable cause) {
        return failure(Reason.PROVIDER_UNAVAILABLE, Message.STT_FAILED_UNAVAILABLE, true, cause);
    }

    public static SpeechTranscriptionException malformedOutput(Throwable cause) {
        return failure(Reason.MALFORMED_OUTPUT, Message.STT_FAILED_INVALID_OUTPUT, false, cause);
    }

    public static SpeechTranscriptionException audioUnavailable(Throwable cause) {
        return failure(Reason.AUDIO_UNAVAILABLE, Message.STT_FAILED_AUDIO_UNAVAILABLE, true, cause);
    }

    public static SpeechTranscriptionException unexpected(Throwable cause) {
        return failure(Reason.UNEXPECTED, Message.STT_FAILED_UNEXPECTED, false, cause);
    }

    private static SpeechTranscriptionException failure(
            Reason reason,
            String statusMessage,
            boolean retryable,
            Throwable cause) {
        return new SpeechTranscriptionException(
                reason,
                statusMessage,
                retryable,
                "Speech transcription failed: " + reason,
                cause);
    }
}

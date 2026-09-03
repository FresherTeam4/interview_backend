package com.baseProject.myBaseProject.interview.ai.gemini;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public final class GeminiFailureClassifier {

    private GeminiFailureClassifier() {
    }

    public enum FailureKind {
        TIMEOUT,
        RATE_LIMITED,
        CREDENTIAL_REJECTED,
        CLIENT_ERROR,
        SERVER_ERROR,
        API_ERROR,
        NETWORK_ERROR,
        UNEXPECTED
    }

    public record ClassifiedFailure(FailureKind kind, int statusCode, String message, RuntimeException cause) {
    }

    public static ClassifiedFailure classify(RuntimeException exception) {
        ClientException clientError = findCause(exception, ClientException.class);
        if (clientError != null) {
            if (clientError.code() == 408) {
                return new ClassifiedFailure(FailureKind.TIMEOUT, 408, "timeout", exception);
            }
            if (clientError.code() == 429) {
                return new ClassifiedFailure(FailureKind.RATE_LIMITED, 429, "HTTP 429", exception);
            }
            if (clientError.code() == 401 || clientError.code() == 403) {
                return new ClassifiedFailure(FailureKind.CREDENTIAL_REJECTED, clientError.code(), "credential rejected", exception);
            }
            return new ClassifiedFailure(FailureKind.CLIENT_ERROR, clientError.code(), "HTTP " + clientError.code(), exception);
        }

        ServerException serverError = findCause(exception, ServerException.class);
        if (serverError != null) {
            return new ClassifiedFailure(FailureKind.SERVER_ERROR, serverError.code(), "HTTP " + serverError.code(), exception);
        }

        ApiException apiError = findCause(exception, ApiException.class);
        if (apiError != null) {
            return new ClassifiedFailure(FailureKind.API_ERROR, apiError.code(), "HTTP " + apiError.code(), exception);
        }

        if (hasTimeoutCause(exception)) {
            return new ClassifiedFailure(FailureKind.TIMEOUT, 0, "timeout", exception);
        }

        if (findCause(exception, GenAiIOException.class) != null) {
            return new ClassifiedFailure(FailureKind.NETWORK_ERROR, 0, "network connection failed", exception);
        }

        return new ClassifiedFailure(FailureKind.UNEXPECTED, 0, "unexpected", exception);
    }

    public static boolean hasTimeoutCause(Throwable throwable) {
        return findCause(throwable, InterruptedIOException.class) != null
                || findCause(throwable, HttpTimeoutException.class) != null
                || findCause(throwable, TimeoutException.class) != null;
    }

    public static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return null;
    }
}

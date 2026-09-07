package com.baseProject.myBaseProject.util;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;

public final class IdempotencyKeyNormalizer {
    private static final int MAX_LENGTH = 100;

    private IdempotencyKeyNormalizer() {
    }

    public static String normalize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key header is required");
        }
        String value = rawValue.strip();
        if (value.length() > MAX_LENGTH) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must not exceed " + MAX_LENGTH + " characters");
        }
        return value;
    }
}

package com.baseProject.myBaseProject.interview.validation;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;

import java.util.List;
import java.util.Locale;

final class AiOutputValidationSupport {
    private final ErrorCode errorCode;
    private final String requiredFieldMessage;

    AiOutputValidationSupport(ErrorCode errorCode, String requiredFieldMessage) {
        this.errorCode = errorCode;
        this.requiredFieldMessage = requiredFieldMessage;
    }

    String required(String value, int maxLength, String field) {
        String normalized = text(value, maxLength);
        if (normalized == null) {
            throw invalid(requiredFieldMessage.formatted(field));
        }
        return normalized;
    }

    String text(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() || normalized.length() > maxLength ? null : normalized;
    }

    String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    DomainException invalid(String detail) {
        return new DomainException(errorCode, detail);
    }
}

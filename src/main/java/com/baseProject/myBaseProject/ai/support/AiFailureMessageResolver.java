package com.baseProject.myBaseProject.ai.support;

import com.baseProject.myBaseProject.exception.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

public final class AiFailureMessageResolver {
    private static final Set<ErrorCode> PUBLIC_ERROR_CODES = EnumSet.of(
            ErrorCode.AI_TIMEOUT,
            ErrorCode.AI_SERVICE_UNAVAILABLE);

    private AiFailureMessageResolver() {
    }

    public static String resolve(ErrorCode errorCode, ErrorCode fallbackErrorCode) {
        return PUBLIC_ERROR_CODES.contains(errorCode)
                ? errorCode.getDefaultMessage()
                : fallbackErrorCode.getDefaultMessage();
    }
}

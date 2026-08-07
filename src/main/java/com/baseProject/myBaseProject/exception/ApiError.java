package com.baseProject.myBaseProject.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError (
    Instant timestamp,
    int status,
    ErrorCode code,
    String message,
    String path,
    Map<String, String> fieldErrors
){
    public static ApiError of(Instant timestamp, int status, ErrorCode code, String message, String path) {
        return new ApiError(timestamp, status, code, message, path, null);
    }
}

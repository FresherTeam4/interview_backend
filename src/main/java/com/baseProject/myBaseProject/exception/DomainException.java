package com.baseProject.myBaseProject.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DomainException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;

    protected DomainException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Dùng khi lỗi gốc đến từ một hệ thống khác (object storage, HTTP client) và cần giữ
     * stacktrace để đọc log. {@code message} vẫn là câu cho người dùng, {@code cause} chỉ
     * để log — {@link GlobalExceptionHandler} không bao giờ đưa cause vào response.
     */
    protected DomainException(ErrorCode code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }
}

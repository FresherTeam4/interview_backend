package com.baseProject.myBaseProject.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AiException extends DomainException {

    public AiException(ErrorCode code) {
        super(code);
    }

    public AiException(ErrorCode code, String message) {
        super(code, message);
    }

    public AiException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public AiException(ErrorCode code, Throwable cause) {
        super(code, cause);
    }

    public AiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }
}

package com.baseProject.myBaseProject.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DomainException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;

    public DomainException(ErrorCode code) {
        super(code.getDefaultMessage());
        this.code = code;
        this.status = code.getStatus();
    }

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.status = code.getStatus();
    }

    public DomainException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = code.getStatus();
    }

    public DomainException(ErrorCode code, Throwable cause) {
        super(code.getDefaultMessage(), cause);
        this.code = code;
        this.status = code.getStatus();
    }

    public DomainException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}

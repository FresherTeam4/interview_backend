package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class StorageException extends DomainException {

    public StorageException(String message) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public StorageException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
        if (cause != null) {
            initCause(cause);
        }
    }
}

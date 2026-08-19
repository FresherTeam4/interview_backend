package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class InvalidGoogleTokenException extends DomainException {

    public InvalidGoogleTokenException(String message) {
        super(ErrorCode.INVALID_GOOGLE_TOKEN, HttpStatus.UNAUTHORIZED, message);
    }
}

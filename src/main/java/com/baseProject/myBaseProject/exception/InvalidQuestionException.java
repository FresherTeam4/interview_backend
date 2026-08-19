package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class InvalidQuestionException extends DomainException {
    public InvalidQuestionException(String message) {
        super(ErrorCode.INVALID_QUESTION, HttpStatus.BAD_REQUEST, message);
    }
}

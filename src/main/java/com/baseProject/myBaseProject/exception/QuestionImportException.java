package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class QuestionImportException extends DomainException {
    public QuestionImportException(String message) {
        super(ErrorCode.INVALID_QUESTION_IMPORT, HttpStatus.BAD_REQUEST, message);
    }
}

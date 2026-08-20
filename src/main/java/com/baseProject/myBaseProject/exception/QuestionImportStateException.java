package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class QuestionImportStateException extends DomainException {
    public QuestionImportStateException(String message) {
        super(ErrorCode.INVALID_QUESTION_IMPORT_STATE, HttpStatus.CONFLICT, message);
    }
}

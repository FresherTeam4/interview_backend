package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class DuplicateQuestionException extends DomainException {
    public DuplicateQuestionException(Long existingQuestionId) {
        super(
                ErrorCode.DUPLICATE_QUESTION,
                HttpStatus.CONFLICT,
                "Question content already exists as question %d".formatted(existingQuestionId)
        );
    }
}

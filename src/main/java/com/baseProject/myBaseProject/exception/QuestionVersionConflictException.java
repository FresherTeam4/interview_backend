package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class QuestionVersionConflictException extends DomainException {
    public QuestionVersionConflictException(Long questionId) {
        super(
                ErrorCode.QUESTION_VERSION_CONFLICT,
                HttpStatus.CONFLICT,
                "Question %d was modified by another request. Reload it before updating.".formatted(questionId)
        );
    }
}

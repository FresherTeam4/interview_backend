package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class RubricNotAvailableException extends DomainException {

    public RubricNotAvailableException() {
        super(
                ErrorCode.RUBRIC_NOT_AVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                Message.RUBRIC_NOT_AVAILABLE);
    }
}

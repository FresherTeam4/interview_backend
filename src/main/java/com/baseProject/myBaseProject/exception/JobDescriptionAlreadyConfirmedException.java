package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionAlreadyConfirmedException extends DomainException {

    public JobDescriptionAlreadyConfirmedException() {
        super(
                ErrorCode.JD_ALREADY_CONFIRMED,
                HttpStatus.CONFLICT,
                Message.JD_ALREADY_CONFIRMED);
    }
}

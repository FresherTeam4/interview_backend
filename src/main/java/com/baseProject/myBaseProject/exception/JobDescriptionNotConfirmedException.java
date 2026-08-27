package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionNotConfirmedException extends DomainException {

    public JobDescriptionNotConfirmedException() {
        super(ErrorCode.JD_NOT_CONFIRMED, HttpStatus.CONFLICT, Message.JD_NOT_CONFIRMED);
    }
}

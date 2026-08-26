package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionNotFoundException extends DomainException {

    public JobDescriptionNotFoundException() {
        super(ErrorCode.JD_NOT_FOUND, HttpStatus.NOT_FOUND, Message.JD_NOT_FOUND);
    }
}
